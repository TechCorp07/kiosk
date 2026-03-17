package com.blitztech.pudokiosk.ui.courier

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.courier.TransactionRequest
import com.blitztech.pudokiosk.data.api.dto.courier.TransactionResponse
import com.blitztech.pudokiosk.databinding.ActivityCourierDeliverBinding
import com.blitztech.pudokiosk.deviceio.DoorMonitor
import com.blitztech.pudokiosk.deviceio.HardwareManager
import com.blitztech.pudokiosk.deviceio.rs232.BarcodeScanner
import com.blitztech.pudokiosk.deviceio.camera.SecurityCameraManager
import com.blitztech.pudokiosk.deviceio.camera.PhotoReason
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Courier Delivery / Drop-off Flow
 *
 * Backend model: POST /api/v1/transactions/courier/dropoff
 * Each scan triggers a dropoff transaction that returns the assigned cell.
 *
 * Flow:
 * 1. Screen shows "Scan parcel barcode to begin drop-off"
 * 2. Courier scans parcel barcode
 * 3. Call courierDropoff → TransactionResponse with cellNumber
 * 4. Kiosk opens assigned locker cell
 * 5. DoorMonitor waits for close
 * 6. Show success → loop for next parcel
 * 7. "Finish Drop-off" button ends session
 */
class CourierDeliverActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityCourierDeliverBinding
    private lateinit var prefs: Prefs
    private val hw: HardwareManager by lazy { HardwareManager.getInstance(this) }
    private val api by lazy { ZimpudoApp.apiRepository }

    private var deliveredCount = 0
    private var doorMonitor: DoorMonitor? = null
    private var scanJob: kotlinx.coroutines.Job? = null
    private var isWaitingForDoor = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCourierDeliverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        binding.btnBack.setOnClickListener {
            if (!isWaitingForDoor) finishSafely()
        }
        binding.btnFinishDropoff.setOnClickListener {
            if (!isWaitingForDoor) {
                showSummaryAndExit()
            } else {
                showToast("Please close the locker door first.")
            }
        }

        enterScanMode()
    }

    // ── Scan mode ────────────────────────────────────────────────
    private fun enterScanMode() {
        isWaitingForDoor = false
        updateCounter()
        showStatus("📦 Scan a parcel barcode to drop off")
        binding.btnFinishDropoff.isEnabled = true
        startScanListening()
    }

    private fun startScanListening() {
        scanJob?.cancel()
        scanJob = lifecycleScope.launch {
            BarcodeScanner.scannedData.collect { scannedCode ->
                scanJob?.cancel()
                showStatus("Looking up parcel: $scannedCode…")
                processDropoff(scannedCode)
            }
        }
    }

    // ── Transaction flow ─────────────────────────────────────────
    private fun processDropoff(trackingNumber: String) {
        lifecycleScope.launch {
            val token = prefs.getAccessToken() ?: ""
            val kioskId = prefs.getLocationId().ifBlank { "KIOSK-001" }

            val request = TransactionRequest(
                trackingNumber = trackingNumber,
                kioskId = kioskId
            )
            val result = api.courierDropoff(request, token)

            when (result) {
                is NetworkResult.Success -> {
                    val resp = result.data
                    if (!resp.success) {
                        showToast("Dropoff failed: ${resp.message}")
                        enterScanMode()
                        return@launch
                    }
                    val cellNumber = resp.cellNumber
                    if (cellNumber != null && cellNumber > 0) {
                        showStatus("✅ Assigned to Locker $cellNumber\nOpening…")
                        openLockerForDropoff(resp)
                    } else {
                        showToast("No locker cell assigned: ${resp.message}")
                        enterScanMode()
                    }
                }
                is NetworkResult.Error -> {
                    showToast("Dropoff failed: ${result.message}")
                    enterScanMode()
                }
                is NetworkResult.Loading<*> -> { /* no-op */ }
            }
        }
    }

    // ── Open locker ──────────────────────────────────────────────
    private fun openLockerForDropoff(txn: TransactionResponse) {
        isWaitingForDoor = true
        binding.btnFinishDropoff.isEnabled = false
        val cellNumber = txn.cellNumber ?: return

        lifecycleScope.launch {
            try {
                val locker = hw.getLocker() ?: run {
                    showToast("Locker hardware unavailable")
                    enterScanMode()
                    return@launch
                }
                if (!locker.isConnected) locker.connect()

                // Security photo before locker opens
                SecurityCameraManager.getInstance(this@CourierDeliverActivity).captureSecurityPhoto(
                    reason = PhotoReason.COURIER_DELIVER,
                    referenceId = txn.transactionId ?: txn.orderId ?: "",
                    userId = prefs.getString("courier_id", "")
                )

                val result = locker.unlockLock(cellNumber)
                if (!result.success) {
                    showToast("Failed to open locker: ${result.errorMessage}")
                    enterScanMode()
                    return@launch
                }

                showStatus("🔓 Locker $cellNumber open!\n\nPlace the parcel inside and close the door.")

                doorMonitor?.stop()
                doorMonitor = DoorMonitor(locker, cellNumber, lifecycleScope).also {
                    it.start(
                        onDoorOpenTooLong = {
                            hw.speaker.startDoorCloseReminder()
                        },
                        onDoorTimeout = {
                            hw.speaker.stopDoorCloseReminder()
                            showToast("Door timeout — please close the locker.")
                        },
                        onDoorClosed = {
                            hw.speaker.stopDoorCloseReminder()
                            hw.speaker.playSuccessChime()
                            onDropoffComplete()
                        }
                    )
                }
            } catch (e: Exception) {
                showToast("Error: ${e.message}")
                enterScanMode()
            }
        }
    }

    private fun onDropoffComplete() {
        deliveredCount++
        lifecycleScope.launch {
            delay(500)
            enterScanMode()
        }
    }

    // ── Summary & exit ───────────────────────────────────────────
    private fun showSummaryAndExit() {
        scanJob?.cancel()
        showStatus("✅ Drop-off complete!\n$deliveredCount parcel(s) delivered.\nThank you!")
        binding.btnFinishDropoff.isEnabled = false
        lifecycleScope.launch {
            delay(3000)
            finishSafely()
        }
    }

    private fun updateCounter() {
        binding.tvDeliveredCount.text = "Parcels delivered this session: $deliveredCount"
    }

    private fun showStatus(msg: String) {
        binding.tvStatus.text = msg
        binding.tvStatus.visibility = View.VISIBLE
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        super.onDestroy()
        scanJob?.cancel()
        doorMonitor?.stop()
        hw.speaker.stopDoorCloseReminder()
    }
}
