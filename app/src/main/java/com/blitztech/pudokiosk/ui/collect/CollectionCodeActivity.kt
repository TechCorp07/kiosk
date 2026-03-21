package com.blitztech.pudokiosk.ui.collect

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.collection.RecipientAuthRequest
import com.blitztech.pudokiosk.data.api.dto.collection.LockerOpenRequest
import com.blitztech.pudokiosk.data.api.dto.collection.LockerPickupRequest
import com.blitztech.pudokiosk.databinding.ActivityCollectionCodeBinding
import com.blitztech.pudokiosk.deviceio.DoorMonitor
import com.blitztech.pudokiosk.deviceio.HardwareManager
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.blitztech.pudokiosk.deviceio.camera.SecurityCameraManager
import com.blitztech.pudokiosk.deviceio.camera.PhotoReason
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Recipient Package Collection Screen
 *
 * Backend flow:
 * 1. POST /api/v1/locker/recipient/auth  → RecipientAuthResponse (cellNumber, lockerId, orderId)
 * 2. POST /api/v1/locker/open            → open the cell (LockerOpenRequest)
 * 3. DoorMonitor waits for close
 * 4. POST /api/v1/locker/pickup           → confirm pickup (LockerPickupRequest)
 */
class CollectionCodeActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityCollectionCodeBinding
    private lateinit var prefs: Prefs
    private val hw: HardwareManager by lazy { HardwareManager.getInstance(this) }
    private val api by lazy { ZimpudoApp.apiRepository }

    private var orderId: String = ""
    private var lockerId: String = ""
    private var assignedCellNumber: Int = 0
    private var doorMonitor: DoorMonitor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCollectionCodeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = ZimpudoApp.prefs
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finishSafely() }
        binding.btnOpenLocker.setOnClickListener {
            val code = binding.etCollectionCode.text.toString().trim()
            if (code.isEmpty()) {
                binding.tilCollectionCode.error = "Please enter your collection code"
                return@setOnClickListener
            }
            binding.tilCollectionCode.error = null
            validateCode(code)
        }
    }

    // ── Step 1: Authenticate recipient with collection code ──────
    private fun validateCode(code: String) {
        setLoading(true)
        lifecycleScope.launch {
            val kioskId = prefs.getLocationId().ifBlank { "KIOSK-001" }
            val request = RecipientAuthRequest(
                collectionCode = code,
                kioskId = kioskId
            )
            val result = api.authenticateRecipient(request)
            setLoading(false)

            when (result) {
                is NetworkResult.Success -> {
                    val resp = result.data
                    if (!resp.success) {
                        showError("Invalid or expired collection code: ${resp.message}")
                        return@launch
                    }
                    orderId = resp.orderId ?: ""
                    lockerId = resp.lockerId ?: ""
                    assignedCellNumber = resp.cellNumber ?: 0

                    if (assignedCellNumber <= 0) {
                        showError("No locker cell assigned. Please contact support.")
                        return@launch
                    }

                    val recipientInfo = resp.recipientName ?: "your parcel"
                    showStatus("✅ Code verified!\nLocker $assignedCellNumber for $recipientInfo\nOpening…")

                    // Security photo before locker opens
                    SecurityCameraManager.getInstance(this@CollectionCodeActivity).captureSecurityPhoto(
                        reason = PhotoReason.CLIENT_COLLECTION,
                        referenceId = orderId,
                        userId = prefs.getUserMobile() ?: ""
                    )

                    // Step 2: Open the cell
                    openCell()
                }
                is NetworkResult.Error -> showError("Error: ${result.message}")
                is NetworkResult.Loading<*> -> { /* handled via setLoading */ }
            }
        }
    }

    // ── Step 2: Open the assigned locker cell ────────────────────
    private fun openCell() {
        lifecycleScope.launch {
            val token = prefs.getAccessToken() ?: ""

            // Tell backend to open the cell
            if (lockerId.isNotBlank()) {
                val openReq = LockerOpenRequest(lockerId = lockerId, cellNumber = assignedCellNumber)
                api.openCell(openReq, token) // best-effort — kiosk also opens locally
            }

            // Open via local hardware
            try {
                val lockerController = hw.getLocker() ?: run {
                    showError("Locker system unavailable. Please contact support.")
                    return@launch
                }
                if (!lockerController.isConnected) {
                    val connected = lockerController.connect()
                    if (!connected) {
                        showError("Cannot connect to locker hardware.")
                        return@launch
                    }
                }
                val unlockResult = lockerController.unlockLock(assignedCellNumber)
                if (!unlockResult.success) {
                    showError("Failed to open locker: ${unlockResult.errorMessage}")
                    return@launch
                }

                showStatus("🔓 Locker $assignedCellNumber is open!\nPlease collect your parcel and close the door.")
                binding.btnOpenLocker.isEnabled = false
                binding.etCollectionCode.isEnabled = false

                // Step 3: Wait for door close
                doorMonitor = DoorMonitor(lockerController, assignedCellNumber, lifecycleScope).also {
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

    // ── Step 4: Confirm pickup with backend ──────────────────────
    private fun confirmPickup() {
        lifecycleScope.launch {
            try {
                val token = prefs.getAccessToken() ?: ""
                val kioskId = prefs.getLocationId().ifBlank { "KIOSK-001" }
                val request = LockerPickupRequest(orderId = orderId, kioskId = kioskId)
                api.completePickup(request, token)
            } catch (_: Exception) { /* best-effort */ }

            showStatus("✅ Collection complete!\nThank you for using ZIMPUDO.")
            delay(4000)
            finishSafely()
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
        hw.speaker.stopDoorCloseReminder()
    }
}
