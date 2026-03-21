package com.blitztech.pudokiosk.ui.courier

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.courier.TransactionRequest
import com.blitztech.pudokiosk.data.api.dto.courier.TransactionResponse
import com.blitztech.pudokiosk.databinding.ActivityCourierCollectBinding
import com.blitztech.pudokiosk.deviceio.DoorMonitor
import com.blitztech.pudokiosk.deviceio.HardwareManager
import com.blitztech.pudokiosk.deviceio.printer.CustomTG2480HIIIDriver
import com.blitztech.pudokiosk.deviceio.rs232.BarcodeScanner
import com.blitztech.pudokiosk.deviceio.camera.SecurityCameraManager
import com.blitztech.pudokiosk.deviceio.camera.PhotoReason
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.blitztech.pudokiosk.ui.main.CourierMainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Courier Collection Flow (scan-first → transaction)
 *
 * Backend model: POST /api/v1/transactions/courier/pickup
 * Each scan triggers a pickup transaction that returns the assigned cell.
 *
 * Flow:
 * 1. Screen enters scan mode — prompts courier to scan parcel barcode
 * 2. Courier scans barcode → call courierPickup with tracking number
 * 3. Backend returns TransactionResponse with cellNumber
 * 4. Kiosk opens the assigned locker cell
 * 5. DoorMonitor waits for close → plays reminder if needed
 * 6. Show success → loop back to scan mode for next parcel
 * 7. "Finish Collection" button ends the session
 */
class CourierCollectActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityCourierCollectBinding
    private lateinit var prefs: Prefs
    private val hw: HardwareManager by lazy { HardwareManager.getInstance(this) }
    private val api by lazy { ZimpudoApp.apiRepository }
    private val printer: CustomTG2480HIIIDriver by lazy { CustomTG2480HIIIDriver(this) }
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private var collectedCount = 0
    private var doorMonitor: DoorMonitor? = null
    private var scanJob: Job? = null
    private var idleJob: Job? = null
    private var isWaitingForDoor = false
    private var lastTxnTracking: String = ""
    private var lastTxnCell: Int = 0

    companion object {
        private const val IDLE_TIMEOUT_MS = 2 * 60 * 1000L // 2 minutes
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCourierCollectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = ZimpudoApp.prefs

        binding.btnBack.setOnClickListener {
            if (!isWaitingForDoor) finishSafely()
        }
        binding.btnFinish.setOnClickListener {
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
        showStatus("📦 Scan a parcel barcode to collect")
        binding.btnFinish.isEnabled = true
        startScanListening()
        resetIdleTimer()
    }

    private fun startScanListening() {
        scanJob?.cancel()
        scanJob = lifecycleScope.launch {
            BarcodeScanner.scannedData.collect { scannedCode ->
                scanJob?.cancel()
                resetIdleTimer()
                showStatus("Looking up parcel: $scannedCode…")
                processPickup(scannedCode)
            }
        }
    }

    // ── Transaction flow ─────────────────────────────────────────
    private fun processPickup(trackingNumber: String) {
        lifecycleScope.launch {
            val token = prefs.getAccessToken() ?: ""
            val kioskId = prefs.getLocationId().ifBlank { "KIOSK-001" }

            val request = TransactionRequest(
                trackingNumber = trackingNumber,
                kioskId = kioskId
            )
            val result = api.courierPickup(request, token)

            when (result) {
                is NetworkResult.Success -> {
                    val resp = result.data
                    if (!resp.success) {
                        showToast("Pickup failed: ${resp.message}")
                        enterScanMode()
                        return@launch
                    }
                    val cellNumber = resp.cellNumber
                    if (cellNumber != null && cellNumber > 0) {
                        showStatus("✅ Found! Opening Locker $cellNumber…\n${resp.recipientName ?: ""}")
                        lastTxnTracking = trackingNumber
                        lastTxnCell = cellNumber
                        openLockerForCollection(resp)
                    } else {
                        showToast("No locker assigned for this parcel.")
                        enterScanMode()
                    }
                }
                is NetworkResult.Error -> {
                    showToast("Pickup failed: ${result.message}")
                    enterScanMode()
                }
                is NetworkResult.Loading<*> -> { /* no-op */ }
            }
        }
    }

    // ── Open locker ──────────────────────────────────────────────
    private fun openLockerForCollection(txn: TransactionResponse) {
        isWaitingForDoor = true
        binding.btnFinish.isEnabled = false
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
                SecurityCameraManager.getInstance(this@CourierCollectActivity).captureSecurityPhoto(
                    reason = PhotoReason.COURIER_COLLECT,
                    referenceId = txn.transactionId ?: txn.orderId ?: "",
                    userId = prefs.getString("courier_id", "")
                )

                val result = locker.unlockLock(cellNumber)
                if (!result.success) {
                    showToast("Failed to open locker: ${result.errorMessage}")
                    enterScanMode()
                    return@launch
                }

                showStatus("🔓 Locker $cellNumber open!\nCollect the parcel, then close the door.")

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
                            onPickupComplete()
                        }
                    )
                }
            } catch (e: Exception) {
                showToast("Error: ${e.message}")
                enterScanMode()
            }
        }
    }

    private fun onPickupComplete() {
        collectedCount++
        lifecycleScope.launch {
            printCourierReceipt(lastTxnTracking, lastTxnCell)
            delay(500)
            enterScanMode()
        }
    }

    // ── Idle timeout ─────────────────────────────────────────────
    private fun resetIdleTimer() {
        idleJob?.cancel()
        idleJob = lifecycleScope.launch {
            delay(IDLE_TIMEOUT_MS)
            showToast("Session idle — returning to main menu.")
            navigateToCourierMain()
        }
    }

    private fun navigateToCourierMain() {
        scanJob?.cancel()
        doorMonitor?.stop()
        val intent = Intent(this, CourierMainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    // ── Receipt printing ──────────────────────────────────────────
    private suspend fun printCourierReceipt(trackingNumber: String, cellNumber: Int) {
        try {
            val kioskId = prefs.getLocationId().ifBlank { "KIOSK-001" }
            val receipt = buildString {
                appendLine("    ZIMPUDO KIOSK")
                appendLine("    PICK-UP RECEIPT")
                appendLine("================================")
                appendLine("Date: ${dateFormatter.format(Date())}")
                appendLine("Kiosk: $kioskId")
                appendLine("Courier: ${prefs.getUserMobile() ?: "-"}")
                appendLine("Tracking: $trackingNumber")
                appendLine("Locker Cell: $cellNumber")
                appendLine("================================")
                appendLine("Parcel collected from locker.")
                appendLine("Deliver to recipient promptly.")
                appendLine()
            }
            val result = printer.printText(receipt, fontSize = 1, centered = false)
            if (result.isSuccess) printer.feedAndCut()
        } catch (_: Exception) { /* best-effort */ }
    }

    // ── Summary & exit ───────────────────────────────────────────
    private fun showSummaryAndExit() {
        scanJob?.cancel()
        showStatus("✅ Collection complete!\n$collectedCount parcel(s) collected.\nHave a safe journey!")
        binding.btnFinish.isEnabled = false
        lifecycleScope.launch {
            delay(3000)
            finishSafely()
        }
    }

    private fun updateCounter() {
        binding.tvStatus.text = "Parcels collected this session: $collectedCount"
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
        idleJob?.cancel()
        doorMonitor?.stop()
        hw.speaker.stopDoorCloseReminder()
    }
}
