package com.blitztech.pudokiosk.ui.courier

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.db.OutboxEventEntity
import com.blitztech.pudokiosk.databinding.ActivityCourierCollectBinding
import com.blitztech.pudokiosk.deviceio.DoorMonitor
import com.blitztech.pudokiosk.deviceio.HardwareManager
import com.blitztech.pudokiosk.deviceio.printer.CustomTG2480HIIIDriver
import com.blitztech.pudokiosk.deviceio.rs232.BarcodeScanner
import com.blitztech.pudokiosk.deviceio.camera.SecurityCameraManager
import com.blitztech.pudokiosk.deviceio.camera.PhotoReason
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.sync.SyncScheduler
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
 * Courier Collection Flow — orders-service pickup-scan pattern.
 *
 * On field operations: courier arrives at origin PUDO kiosk to collect parcel(s)
 * that a sender deposited. The courier scans the barcode on the parcel / label.
 *
 * Flow:
 * 1. Screen loads pending parcels from Room DB where status = IN_LOCKER.
 * 2. Courier selects parcels to collect.
 * 3. Open the locker cells one by one via RS485.
 * 4. DoorMonitor waits for door close -> plays reminder if left open.
 * 5. POST /orders/{orderId}/pickup-scan?barcode=... -> confirm with backend.
 * 6. On backend failure -> write to outbox, auto-retry when online.
 * 7. When all selected parcels are processed, prints detailed manifest and ends session.
 */
class CourierCollectActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityCourierCollectBinding
    private lateinit var prefs: Prefs
    private val hw: HardwareManager by lazy { HardwareManager.getInstance(this) }
    private val api by lazy { ZimpudoApp.apiRepository }
    private val db by lazy { ZimpudoApp.database }
    private val printer: CustomTG2480HIIIDriver by lazy { CustomTG2480HIIIDriver(this) }
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private var collectedCount = 0
    private var doorMonitor: DoorMonitor? = null
    private var scanJob: Job? = null
    private var idleJob: Job? = null
    private var isWaitingForDoor = false

    // State for batch processing
    private val selectedParcels = mutableListOf<com.blitztech.pudokiosk.data.api.dto.courier.CourierParcel>()
    private val collectedParcels = mutableListOf<com.blitztech.pudokiosk.data.api.dto.courier.CourierParcel>()
    private var currentCollectIndex = 0

    companion object {
        private const val TAG = "CourierCollectActivity"
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

        loadPendingParcels()
    }

    private fun loadPendingParcels() {
        lifecycleScope.launch {
            val token = prefs.getAccessToken().orEmpty()
            val lockerId = prefs.getPrimaryLockerUuid()

            // 1. Sync pending collections from Backend
            showStatus("Syncing collections from backend...")
            if (token.isNotBlank() && lockerId.isNotBlank()) {
                when (val response = api.courierPickupFromLocker(lockerId, token)) {
                    is NetworkResult.Success -> {
                        val packages = response.data.pickedUpPackages ?: emptyList()
                        // Upsert into local database
                        // waybillNumber = actual tracking number (e.g. CB2440353187ZW)
                        // orderId = backend UUID — stored in senderId field for courier reference
                        packages.forEach { pkg ->
                            if (!pkg.orderId.isNullOrBlank()) {
                                db.parcels().upsert(
                                    com.blitztech.pudokiosk.data.db.ParcelEntity(
                                        id = pkg.cellId ?: java.util.UUID.randomUUID().toString(),
                                        trackingCode = pkg.waybillNumber ?: pkg.orderId,
                                        lockNumber = pkg.cellNumber ?: 0,
                                        status = "IN_LOCKER",
                                        size = "M",
                                        recipientId = "PUDO Recipient",
                                        senderId = pkg.orderId,  // orderId stored here for pickup-scan call
                                        lockerId = lockerId,
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }
                    else -> {
                        // Keep going, fallback to whatever is already in local DB
                        android.util.Log.w(TAG, "Failed to sync collections from backend")
                    }
                }
            }

            // 2. Load from local Room DB
            val parcels = db.parcels().getByStatus("IN_LOCKER")

            if (parcels.isEmpty()) {
                showStatus("No pending parcels for collection on this kiosk.")
                binding.btnFinish.visibility = View.VISIBLE
            } else {
                binding.parcelListGroup.visibility = View.VISIBLE
                showStatus("Select parcels to collect:")
                val courierParcels = parcels.map {
                    com.blitztech.pudokiosk.data.api.dto.courier.CourierParcel(
                        parcelId = it.id,
                        orderId = it.senderId,        // backend UUID stored in senderId
                        lockNumber = it.lockNumber,
                        tracking = it.trackingCode,   // actual tracking number (e.g. CB2440353187ZW)
                        size = it.size,
                        recipientName = it.recipientId,
                        status = it.status
                    )
                }

                val adapter = CourierParcelAdapter(courierParcels) { selected ->
                    selectedParcels.clear()
                    selectedParcels.addAll(selected)
                    binding.btnStartCollection.isEnabled = selected.isNotEmpty()
                }
                binding.rvParcels.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@CourierCollectActivity)
                binding.rvParcels.adapter = adapter

                binding.btnStartCollection.setOnClickListener {
                    binding.parcelListGroup.visibility = View.GONE
                    binding.btnFinish.visibility = View.GONE
                    currentCollectIndex = 0
                    collectedCount = 0
                    collectedParcels.clear()
                    startCollectionBatch()
                }
            }
        }
    }

    private fun startCollectionBatch() {
        if (currentCollectIndex < selectedParcels.size) {
            val parcel = selectedParcels[currentCollectIndex]
            showStatus("📦 Collecting: ${parcel.tracking} from Cell ${parcel.lockNumber}")
            
            lifecycleScope.launch {
                val token = prefs.getAccessToken().orEmpty()
                openLockerForCollection(parcel.tracking, parcel.orderId, parcel.lockNumber, parcel.parcelId, token)
            }
        } else {
            showStatus("✅ All selected parcels collected.")
            lifecycleScope.launch {
                printManifest()
                showSummaryAndExit()
            }
        }
    }



    // ── Open locker ───────────────────────────────────────────────────────
    private fun openLockerForCollection(
        barcode: String,
        orderId: String,
        cellNumber: Int,
        cellUuid: String,
        token: String
    ) {
        isWaitingForDoor = true
        binding.btnFinish.isEnabled = false

        lifecycleScope.launch {
            try {
                if (prefs.isHardwareBypassEnabled()) {
                    showStatus("🔓 SIMULATED (DEV BYPASS): Cell $cellNumber open!\nPretending to remove parcel and close door...")
                    // Simulate waiting 3 seconds for door close
                    delay(3000)
                    hw.speaker.playSuccessChime()
                    onPickupComplete(barcode, orderId, cellNumber, cellUuid, token)
                    return@launch
                }

                val locker = hw.getLocker() ?: run {
                    showToast("Locker hardware unavailable")
                    isWaitingForDoor = false
                    binding.btnFinish.isEnabled = true
                    // Skip to next parcel — can't access hardware
                    currentCollectIndex++
                    startCollectionBatch()
                    return@launch
                }
                if (!locker.isConnected) locker.connect()

                // Security photo before locker opens
                try {
                    SecurityCameraManager.getInstance(this@CourierCollectActivity).captureSecurityPhoto(
                        reason = PhotoReason.COURIER_COLLECT,
                        referenceId = orderId,
                        userId = prefs.getString("courier_id", "")
                    )
                } catch (_: Exception) { /* best-effort */ }

                val unlockResult = locker.unlockLock(cellNumber)
                if (unlockResult.status == com.blitztech.pudokiosk.deviceio.rs485.WinnsenProtocol.LockStatus.COOLDOWN) {
                    showToast("This locker was recently used. Please wait ~15 seconds and try again.")
                    isWaitingForDoor = false
                    binding.btnFinish.isEnabled = true
                    // DO NOT advance index, they must try same parcel again
                    return@launch
                } else if (!unlockResult.success) {
                    showToast("Failed to open cell $cellNumber: ${unlockResult.errorMessage}")
                    isWaitingForDoor = false
                    binding.btnFinish.isEnabled = true
                    currentCollectIndex++
                    startCollectionBatch()
                    return@launch
                }

                showStatus("🔓 Cell $cellNumber open!\nRemove the parcel, then close the door.")

                doorMonitor?.stop()
                doorMonitor = DoorMonitor(locker, cellNumber, lifecycleScope).also { monitor ->
                    monitor.start(
                        onDoorOpenTooLong = { hw.speaker.startDoorCloseReminder() },
                        onDoorTimeout = {
                            hw.speaker.stopDoorCloseReminder()
                            showToast("Door timeout — please close the locker.")
                        },
                        onDoorClosed = {
                            hw.speaker.stopDoorCloseReminder()
                            hw.speaker.playSuccessChime()
                            onPickupComplete(barcode, orderId, cellNumber, cellUuid, token)
                        }
                    )
                }

            } catch (e: Exception) {
                showToast("Hardware error: ${e.message}")
                isWaitingForDoor = false
                binding.btnFinish.isEnabled = true
                currentCollectIndex++
                startCollectionBatch()
            }
        }
    }

    // ── Post-door-close: confirm with backend ──────────────────────────────
    private fun onPickupComplete(
        barcode: String,
        orderId: String,
        cellNumber: Int,
        cellUuid: String,
        token: String
    ) {
        isWaitingForDoor = false
        binding.btnFinish.isEnabled = true

        lifecycleScope.launch {
            // Update local state
            val parcel = selectedParcels[currentCollectIndex]
            collectedParcels.add(parcel)
            
            try {
                // Update local parcel status to 'PICKED_UP'
                val localParcel = db.parcels().get(parcel.parcelId)
                if (localParcel != null) {
                    db.parcels().upsert(localParcel.copy(status = "PICKED_UP"))
                }
            } catch (e: Exception) { }

            // Confirm with backend — POST /orders/{orderId}/pickup-scan?barcode=...
            val confirmResult = api.courierPickupScan(orderId, barcode, token)
            when (confirmResult) {
                is NetworkResult.Success -> {
                    android.util.Log.d(TAG, "Pickup confirmed with backend for order $orderId")
                }
                is NetworkResult.Error -> {
                    // Queue to outbox for retry when back online
                    android.util.Log.w(TAG, "Pickup confirmation failed, queuing outbox: ${confirmResult.message}")
                    queuePickupToOutbox(barcode, orderId, cellNumber)
                    // Trigger immediate sync attempt
                    SyncScheduler.enqueueImmediate(applicationContext)
                }
                is NetworkResult.Loading<*> -> { }
            }

            delay(500)
            currentCollectIndex++
            startCollectionBatch()
        }
    }

    // ── Outbox ────────────────────────────────────────────────────────────
    private suspend fun queuePickupToOutbox(barcode: String, orderId: String, cellNumber: Int) {
        try {
            val payload = mapOf(
                "orderId" to orderId,
                "barcode" to barcode,
                "cellNumber" to cellNumber,
                "type" to "COURIER_PICKUP",
                "kioskId" to prefs.getLocationId().ifBlank { "KIOSK-001" },
                "timestamp" to System.currentTimeMillis()
            )
            val payloadJson = moshi.adapter(Map::class.java).toJson(payload)
            val key = "courier_pickup_${orderId}_${System.currentTimeMillis()}"
            db.outbox().insert(
                OutboxEventEntity(
                    idempotencyKey = key,
                    type = "courier_pickup_scan",
                    payloadJson = payloadJson,
                    createdAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to write pickup outbox event", e)
        }
    }

    // ── Receipt printing ──────────────────────────────────────────────────
    private suspend fun printManifest() {
        try {
            if (collectedParcels.isEmpty()) return
            val kioskId = prefs.getLocationId().ifBlank { "KIOSK-001" }
            val manifest = buildString {
                appendLine("    ZIMPUDO KIOSK")
                appendLine("    COURIER MANIFEST")
                appendLine("================================")
                appendLine("Date: ${dateFormatter.format(Date())}")
                appendLine("Kiosk: $kioskId")
                appendLine("Courier: ${prefs.getUserMobile() ?: "-"}")
                appendLine("Total Collected: ${collectedParcels.size}")
                appendLine("================================")
                for (p in collectedParcels) {
                    appendLine("Track: ${p.tracking}")
                    appendLine("Cell: ${p.lockNumber} | Size: ${p.size}")
                    appendLine("-")
                }
                appendLine("================================")
                appendLine("All parcels successfully")
                appendLine("collected for sorting.")
                appendLine()
            }
            val result = printer.printText(manifest, fontSize = 1, centered = false)
            if (result.isSuccess) printer.feedAndCut()
        } catch (_: Exception) { /* best-effort */ }
    }

    // ── Idle timeout ──────────────────────────────────────────────────────
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

    // ── UI helpers ────────────────────────────────────────────────────────
    private fun showStatus(msg: String) {
        runOnUiThread {
            binding.tvStatus.text = msg
            binding.tvStatus.visibility = View.VISIBLE
        }
    }

    private fun showToast(msg: String) = runOnUiThread {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scanJob?.cancel()
        idleJob?.cancel()
        doorMonitor?.stop()
        hw.speaker.stopDoorCloseReminder()
    }

    // ── Summary & exit ────────────────────────────────────────────────────
    private fun showSummaryAndExit() {
        scanJob?.cancel()
        showStatus("✅ Collection complete!\n${collectedParcels.size} parcel(s) collected.\nHave a safe journey!")
        binding.btnFinish.isEnabled = false
        lifecycleScope.launch {
            delay(3000)
            finishSafely()
        }
    }
}
