package com.blitztech.pudokiosk.ui.collect

import com.blitztech.pudokiosk.R

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.collection.RecipientAuthRequest
import com.blitztech.pudokiosk.data.api.dto.collection.LockerPickupRequest
import com.blitztech.pudokiosk.data.db.OutboxEventEntity
import com.blitztech.pudokiosk.databinding.ActivityCollectionCodeBinding
import com.blitztech.pudokiosk.deviceio.DoorMonitor
import com.blitztech.pudokiosk.deviceio.HardwareManager
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.blitztech.pudokiosk.deviceio.camera.SecurityCameraManager
import com.blitztech.pudokiosk.deviceio.camera.PhotoReason
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Recipient Package Collection Screen
 *
 * Backend flow (kiosk-specific endpoints — both public, no auth header):
 * 1. POST /api/v1/locker/recipient/auth  { trackingNumber, credential, authenticationType }
 *    → CollectionValidationResponse { success, message, trackingNumber, cabinetId, cellId, cellNumber }
 * 2. Open the physical cell via RS485 using cellNumber (Option A: provided by backend).
 * 3. DoorMonitor waits for door close.
 * 4. POST /api/v1/locker/pickup  { trackingNumber, authenticationType }
 *    → Confirms collection, sets order to PACKAGE_RETRIEVED
 *
 * Offline fallback: If no network, validate OTP against pre-synced db (OfflineCollectionManager)
 * and write the pickup confirmation to the outbox for later sync.
 */
class CollectionCodeActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityCollectionCodeBinding
    private lateinit var prefs: Prefs
    private val hw: HardwareManager by lazy { HardwareManager.getInstance(this) }
    private val api by lazy { ZimpudoApp.apiRepository }

    // State for this collection session
    private var trackingNumber: String = ""
    private var enteredOtp: String = ""
    private var assignedCellNumber: Int = 0
    private var doorMonitor: DoorMonitor? = null

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCollectionCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = ZimpudoApp.prefs
        setupClickListeners()
        setupFieldHints()
    }

    private fun setupFieldHints() {
        // The layout has two fields: etTrackingNumber and etOtpCode
        // If the kiosk only has one input field (old layout), both fields show in same control
        binding.tilTrackingNumber.hint = getString(R.string.auto_rem_tracking_number_e_g_zp_2026032)
        binding.tilCollectionCode.hint = getString(R.string.auto_rem_collection_otp_6_digit_code_fr)
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finishSafely() }
        binding.btnOpenLocker.setOnClickListener {
            val tracking = binding.etTrackingNumber.text.toString().trim().uppercase()
            val otp = binding.etCollectionCode.text.toString().trim()

            if (tracking.isEmpty()) {
                binding.tilTrackingNumber.error = getString(R.string.auto_rem_please_enter_your_tracking_num)
                return@setOnClickListener
            }
            if (otp.isEmpty() || otp.length < 4) {
                binding.tilCollectionCode.error = getString(R.string.auto_rem_please_enter_your_otp_code)
                return@setOnClickListener
            }
            binding.tilTrackingNumber.error = null
            binding.tilCollectionCode.error = null
            validateCollection(tracking, otp)
        }
    }

    // ── Step 1: Authenticate recipient with tracking# + OTP ──────────────
    private fun validateCollection(tracking: String, otp: String) {
        trackingNumber = tracking
        enteredOtp = otp
        setLoading(true)

        lifecycleScope.launch {
            val online = ZimpudoApp.networkUtils.isOnline(this@CollectionCodeActivity)

            if (online) {
                validateOnline(tracking, otp)
            } else {
                validateOffline(tracking, otp)
            }
        }
    }

    private suspend fun validateOnline(tracking: String, otp: String) {
        val request = RecipientAuthRequest(
            trackingNumber = tracking,
            credential = otp,
            authenticationType = "OTP"
        )
        val token = ZimpudoApp.prefs.getAccessToken() ?: ""
        val result = api.authenticateRecipient(request, token)
        setLoading(false)

        when (result) {
            is NetworkResult.Success -> {
                val resp = result.data
                if (!resp.success) {
                    showError("❌ ${resp.message}")
                    return
                }
                assignedCellNumber = resp.cellNumber ?: 0

                // If backend couldn't resolve the physical door number (cellId is a UUID),
                // resolve it locally from the kiosk's Room DB
                if (assignedCellNumber <= 0 && !resp.cellId.isNullOrBlank()) {
                    val localCell = try {
                        ZimpudoApp.database.cells().getCellByUuid(resp.cellId)
                    } catch (_: Exception) { null }
                    if (localCell != null) {
                        assignedCellNumber = localCell.physicalDoorNumber
                        android.util.Log.d("CollectionCode", "Resolved cellId ${resp.cellId} → door $assignedCellNumber from local DB")
                    }
                }

                if (assignedCellNumber <= 0) {
                    showError("No locker cell assigned — contact support.")
                    return
                }
                showStatus("✅ Verified! Opening cell $assignedCellNumber…")
                captureSecurityPhoto()
                openCell()
            }
            is NetworkResult.Error -> showError("❌ ${result.message}")
            is NetworkResult.Loading<*> -> { /* handled by setLoading */ }
        }
    }

    private suspend fun validateOffline(tracking: String, otp: String) {
        setLoading(false)
        // Attempt local OTP validation via OfflineCollectionManager
        val offline = ZimpudoApp.offlineCollectionManager
        val pendingEntry = offline.validateOffline(tracking, otp)

        if (pendingEntry == null) {
            showError("❌ Invalid tracking number or OTP. (Offline mode)")
            return
        }

        assignedCellNumber = pendingEntry.cellNumber
        if (assignedCellNumber <= 0) {
            showError("No cell data available offline — ensure kiosk has synced recently.")
            return
        }

        showStatus("✅ Verified (offline). Opening cell $assignedCellNumber…")
        captureSecurityPhoto()
        openCell()
    }

    private fun captureSecurityPhoto() {
        lifecycleScope.launch {
            try {
                SecurityCameraManager.getInstance(this@CollectionCodeActivity).captureSecurityPhoto(
                    reason = PhotoReason.CLIENT_COLLECTION,
                    referenceId = trackingNumber,
                    userId = prefs.getUserMobile() ?: ""
                )
            } catch (_: Exception) { /* best-effort photo, don't block collection */ }
        }
    }

    // ── Step 2: Open the assigned locker cell via RS485 ─────────────────
    private fun openCell() {
        lifecycleScope.launch {
            try {
                val locker = hw.getLocker() ?: run {
                    showError("Locker hardware unavailable — contact support.")
                    return@launch
                }
                if (!locker.isConnected) {
                    val connected = locker.connect()
                    if (!connected) {
                        showError("Cannot connect to locker hardware.")
                        return@launch
                    }
                }

                // Call RS485 unlock
                val unlockResult = locker.unlockLock(assignedCellNumber)
                if (unlockResult.status == com.blitztech.pudokiosk.deviceio.rs485.WinnsenProtocol.LockStatus.COOLDOWN) {
                    showError("This locker was recently used. Please wait ~15 seconds and try again.")
                    setLoading(false)
                    return@launch
                } else if (!unlockResult.success) {
                    showError("Hardware error: ${unlockResult.errorMessage}")
                    setLoading(false)
                    return@launch
                }

                showStatus("🔓 Cell $assignedCellNumber is open!\nPlease collect your parcel and close the door.")
                binding.btnOpenLocker.isEnabled = false
                binding.etTrackingNumber.isEnabled = false
                binding.etCollectionCode.isEnabled = false

                // Step 3: Wait for door close
                doorMonitor = DoorMonitor(locker, assignedCellNumber, lifecycleScope).also {
                    it.start(
                        onDoorOpenTooLong = {
                            hw.speaker.startDoorCloseReminder()
                            showStatus("⚠ Please close the locker door!")
                        },
                        onDoorTimeout = {
                            hw.speaker.stopDoorCloseReminder()
                            showError("Session timed out. Please contact staff.")
                            finishSafelyWithDelay(3000)
                        },
                        onDoorClosed = {
                            hw.speaker.stopDoorCloseReminder()
                            hw.speaker.playSuccessChime()
                            confirmPickup()
                        }
                    )
                }
            } catch (e: Exception) {
                showError("Locker error: ${e.message}")
            }
        }
    }

    // ── Step 4: Confirm pickup with backend (or queue to outbox) ─────────
    private fun confirmPickup() {
        lifecycleScope.launch {
            val online = ZimpudoApp.networkUtils.isOnline(this@CollectionCodeActivity)
            val request = LockerPickupRequest(
                trackingNumber = trackingNumber,
                authenticationType = "OTP"
            )

            var confirmed = false
            if (online) {
                val token = ZimpudoApp.prefs.getAccessToken() ?: ""
                val result = api.completePickup(request, token)
                confirmed = result is NetworkResult.Success
            }

            if (!confirmed) {
                // Write to outbox for background sync once connectivity is restored
                writePickupToOutbox(request)
            }

            // Also mark offline entry as collected (if exists)
            ZimpudoApp.offlineCollectionManager.markCollected(trackingNumber)

            showStatus("✅ Collection complete!\nThank you for using ZIMPUDO.")
            delay(4000)
            finishSafely()
        }
    }

    private suspend fun writePickupToOutbox(request: LockerPickupRequest) {
        try {
            val adapter = moshi.adapter(LockerPickupRequest::class.java)
            val payload = adapter.toJson(request)
            val event = OutboxEventEntity(
                idempotencyKey = "collect_${trackingNumber}_${System.currentTimeMillis()}",
                type = "collection_confirmed",
                payloadJson = payload
            )
            ZimpudoApp.database.outbox().insert(event)
        } catch (e: Exception) {
            // Outbox write failure — log but don't block the customer
            android.util.Log.e("CollectionCodeActivity", "Failed to write pickup to outbox", e)
        }
    }

    private fun finishSafelyWithDelay(delayMs: Long) {
        lifecycleScope.launch {
            delay(delayMs)
            finishSafely()
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnOpenLocker.isEnabled = !loading
    }

    private fun showStatus(msg: String) {
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = msg
    }

    private fun showError(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        binding.tvStatus.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        doorMonitor?.stop()
    }
}
