package com.blitztech.pudokiosk.ui.courier

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.config.ApiEndpoints
import com.blitztech.pudokiosk.data.api.dto.courier.CourierOpsResponse
import com.blitztech.pudokiosk.data.db.OutboxEventEntity
import com.blitztech.pudokiosk.databinding.ActivityCourierDeliverBinding
import com.blitztech.pudokiosk.deviceio.DoorMonitor
import com.blitztech.pudokiosk.deviceio.HardwareManager
import com.blitztech.pudokiosk.deviceio.printer.CustomTG2480HIIIDriver
import com.blitztech.pudokiosk.deviceio.rs232.BarcodeScanner
import com.blitztech.pudokiosk.deviceio.camera.SecurityCameraManager
import com.blitztech.pudokiosk.deviceio.camera.PhotoReason
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.blitztech.pudokiosk.ui.main.CourierMainActivity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Courier Delivery / Drop-off Flow
 *
 * Backend: POST /api/v1/orders/{orderId}/dropoff?barcode=...&destinationLockerId=...
 * (Orders Service — uses COURIER role JWT)
 *
 * Flow:
 * 1. Screen shows "Scan parcel barcode to begin drop-off"
 * 2. Courier scans parcel barcode
 * 3. Search order by barcode to resolve orderId
 * 4. Kiosk assigns available cell locally from Room DB
 * 5. Kiosk opens assigned locker cell via RS485
 * 6. DoorMonitor waits for close
 * 7. Call POST /orders/{orderId}/dropoff to confirm with backend
 * 8. Print receipt → loop for next parcel
 * 9. "Finish Drop-off" button ends session
 */
class CourierDeliverActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityCourierDeliverBinding
    private lateinit var prefs: Prefs
    private val hw: HardwareManager by lazy { HardwareManager.getInstance(this) }
    private val api by lazy { ZimpudoApp.apiRepository }
    private val printer: CustomTG2480HIIIDriver by lazy { CustomTG2480HIIIDriver(this) }
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private var deliveredCount = 0
    private var doorMonitor: DoorMonitor? = null
    private var scanJob: Job? = null
    private var idleJob: Job? = null
    private var isWaitingForDoor = false

    // State for the current parcel being processed
    private var lastTxnTracking: String = ""
    private var lastTxnCell: Int = 0
    private var lastTxnOrderId: String = ""

    companion object {
        private const val IDLE_TIMEOUT_MS = 2 * 60 * 1000L // 2 minutes
        private const val TAG = "CourierDeliverActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCourierDeliverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = ZimpudoApp.prefs

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
        binding.btnFinishDropoff.isEnabled = true
        resetIdleTimer()
        // Check locker availability before allowing new scans
        lifecycleScope.launch {
            val freeCells = getFreeCellCount()
            if (freeCells == 0) {
                showStatus("⛔ Kiosk is full — no available locker cells.\nPlease contact support or try another kiosk.")
                binding.btnFinishDropoff.isEnabled = true
                // Don't start scan listening — no point scanning if no cells
            } else {
                val availability = if (freeCells > 0) " ($freeCells cell(s) available)" else ""
                showStatus("📦 Scan a parcel barcode to drop off$availability\n(Tap here to manually input if scanner is unavailable)")
                binding.tvStatus.setOnClickListener {
                    if (prefs.isHardwareBypassEnabled()) {
                        showManualScanDialog()
                    }
                }
                startScanListening()
            }
        }
    }

    private fun showManualScanDialog() {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT
        input.hint = "Tracking Number / Barcode"
        android.app.AlertDialog.Builder(this)
            .setTitle("Manual Barcode Input (DEV BYPASS)")
            .setView(input)
            .setPositiveButton("Submit") { _, _ ->
                val barcode = input.text.toString().trim()
                if (barcode.isNotBlank()) {
                    scanJob?.cancel()
                    resetIdleTimer()
                    showStatus("Looking up parcel (Mock Scan): $barcode…")
                    processDropoff(barcode)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Query Room DB for unoccupied locker cells. Returns -1 if DB unavailable (allow scan). */
    private suspend fun getFreeCellCount(): Int {
        return try {
            val db = ZimpudoApp.database
            val lockerUuid = prefs.getPrimaryLockerUuid()
            db.cells().countAvailable(lockerUuid)
        } catch (_: Exception) {
            -1 // DB unavailable — allow scanning as a safe fallback
        }
    }

    private fun startScanListening() {
        scanJob?.cancel()
        scanJob = lifecycleScope.launch {
            BarcodeScanner.scannedData.collect { scannedCode ->
                scanJob?.cancel()
                resetIdleTimer()           // activity — reset on each scan
                showStatus("Looking up parcel: $scannedCode…")
                processDropoff(scannedCode)
            }
        }
    }

    // ── Transaction flow ───────────────────────────────────────────
    /**
     * Step 1: Search order by barcode → get orderId.
     * Step 2: Assign an available cell locally from Room DB.
     * Step 3: Open the cell.
     * Step 4: After door close → confirm with backend.
     */
    private fun processDropoff(barcode: String) {
        lifecycleScope.launch {
            val token = prefs.getAccessToken() ?: ""

            // Search order to resolve orderId
            showStatus("🔍 Looking up order for: $barcode…")
            val orderResult = api.searchOrder(barcode, token)
            val orderId = when (orderResult) {
                is NetworkResult.Success -> orderResult.data.content.firstOrNull()?.orderId
                else -> null
            }

            if (orderId == null) {
                showToast("⚠️ Parcel not found: $barcode")
                enterScanMode()
                return@launch
            }

            // Assign a cell locally from Room DB
            val lockerUuid = prefs.getPrimaryLockerUuid()
            val cell = try {
                ZimpudoApp.database.cells().getNextAvailableCell(lockerUuid)
            } catch (_: Exception) { null }

            if (cell == null) {
                showToast("⛔ No available cells — kiosk may be full.")
                enterScanMode()
                return@launch
            }

            lastTxnTracking = barcode
            lastTxnCell = cell.physicalDoorNumber
            lastTxnOrderId = orderId

            showStatus("✅ Assigned to cell ${cell.physicalDoorNumber} (${cell.cellSize})… Opening…")

            // Mark cell as occupied locally immediately to prevent double-assignment
            ZimpudoApp.database.cells().markCellOccupied(cell.cellUuid)

            openLockerForDropoff(cell.physicalDoorNumber, orderId, barcode, lockerUuid)
        }
    }

    // ── Open locker ──────────────────────────────────────────────
    private fun openLockerForDropoff(
        cellNumber: Int,
        orderId: String,
        barcode: String,
        destinationLockerId: String
    ) {
        isWaitingForDoor = true
        binding.btnFinishDropoff.isEnabled = false

        lifecycleScope.launch {
            try {
                if (prefs.isHardwareBypassEnabled()) {
                    showStatus("🔓 SIMULATED (DEV BYPASS): Cell $cellNumber open!\nPretending to place parcel inside and close door...")
                    // Simulate waiting 3 seconds for door close
                    delay(3000)
                    hw.speaker.playSuccessChime()
                    confirmDropoffWithBackend(orderId, barcode, destinationLockerId)
                    return@launch
                }

                val locker = hw.getLocker() ?: run {
                    showToast("Locker hardware unavailable")
                    // Free cell reservation on failure
                    ZimpudoApp.database.cells().markCellAvailable(
                        ZimpudoApp.database.cells().getCellByDoorNumber(cellNumber)?.cellUuid ?: ""
                    )
                    enterScanMode()
                    return@launch
                }
                if (!locker.isConnected) locker.connect()

                // Security photo before locker opens
                SecurityCameraManager.getInstance(this@CourierDeliverActivity).captureSecurityPhoto(
                    reason = PhotoReason.COURIER_DELIVER,
                    referenceId = orderId,
                    userId = prefs.getString("courier_id", "")
                )

                val result = locker.unlockLock(cellNumber)
                if (result.status == com.blitztech.pudokiosk.deviceio.rs485.WinnsenProtocol.LockStatus.COOLDOWN) {
                    showToast("This locker was recently used. Please wait ~15 seconds and try again.")
                    ZimpudoApp.database.cells().markCellAvailable(
                        ZimpudoApp.database.cells().getCellByDoorNumber(cellNumber)?.cellUuid ?: ""
                    )
                    enterScanMode()
                    return@launch
                } else if (!result.success) {
                    showToast("Failed to open locker: ${result.errorMessage}")
                    // Free cell reservation
                    ZimpudoApp.database.cells().markCellAvailable(
                        ZimpudoApp.database.cells().getCellByDoorNumber(cellNumber)?.cellUuid ?: ""
                    )
                    enterScanMode()
                    return@launch
                }

                showStatus("🔓 Cell $cellNumber open!\n\nPlace the parcel inside and close the door.")

                doorMonitor?.stop()
                doorMonitor = DoorMonitor(locker, cellNumber, lifecycleScope).also {
                    it.start(
                        onDoorOpenTooLong = { hw.speaker.startDoorCloseReminder() },
                        onDoorTimeout = {
                            hw.speaker.stopDoorCloseReminder()
                            showToast("Door timeout — closing session.")
                            enterScanMode()
                        },
                        onDoorClosed = {
                            hw.speaker.stopDoorCloseReminder()
                            hw.speaker.playSuccessChime()
                            // Confirm with backend after door close
                            confirmDropoffWithBackend(orderId, barcode, destinationLockerId)
                        }
                    )
                }
            } catch (e: Exception) {
                showToast("Error: ${e.message}")
                enterScanMode()
            }
        }
    }

    private fun confirmDropoffWithBackend(orderId: String, barcode: String, destinationLockerId: String) {
        lifecycleScope.launch {
            val token = prefs.getAccessToken() ?: ""
            val dropoffUrl = ApiEndpoints.getCourierDropoffUrl(orderId)

            val result = api.courierDropoffAtLocker(dropoffUrl, barcode, destinationLockerId, token)

            if (result !is NetworkResult.Success || !result.data.success) {
                // Write to outbox for background retry
                writeDropoffToOutbox(orderId, barcode, destinationLockerId)
                android.util.Log.w(TAG, "Dropoff confirmation queued to outbox for $barcode")
            }

            onDropoffComplete()
        }
    }

    private suspend fun writeDropoffToOutbox(orderId: String, barcode: String, destinationLockerId: String) {
        try {
            val payload = """{"orderId":"$orderId","barcode":"$barcode","destinationLockerId":"$destinationLockerId"}"""
            val event = OutboxEventEntity(
                idempotencyKey = "dropoff_${barcode}_${System.currentTimeMillis()}",
                type = "courier_dropoff",
                payloadJson = payload
            )
            ZimpudoApp.database.outbox().insert(event)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to write dropoff to outbox", e)
        }
    }

    private fun onDropoffComplete() {
        deliveredCount++
        lifecycleScope.launch {
            // Print courier receipt for this drop-off
            printCourierReceipt(lastTxnTracking, lastTxnCell)
            delay(500)
            enterScanMode()
        }
    }

    // ── Idle timeout ──────────────────────────────────────────────
    private fun resetIdleTimer() {
        idleJob?.cancel()
        idleJob = lifecycleScope.launch {
            delay(IDLE_TIMEOUT_MS)
            // Timed out — return to courier main dashboard
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
                appendLine("    DROP-OFF RECEIPT")
                appendLine("================================")
                appendLine("Date: ${dateFormatter.format(Date())}")
                appendLine("Kiosk: $kioskId")
                appendLine("Courier: ${prefs.getUserMobile() ?: "-"}")
                appendLine("Tracking: $trackingNumber")
                appendLine("Locker Cell: $cellNumber")
                appendLine("================================")
                appendLine("Parcel received. Customer will")
                appendLine("be notified for collection.")
                appendLine()
            }
            val result = printer.printText(receipt, fontSize = 1, centered = false)
            if (result.isSuccess) printer.feedAndCut()
        } catch (_: Exception) { /* best-effort — don't block flow if printer fails */ }
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
        idleJob?.cancel()
        doorMonitor?.stop()
        hw.speaker.stopDoorCloseReminder()
    }
}
