package com.blitztech.pudokiosk.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.audio.LockerReminder
import com.blitztech.pudokiosk.data.events.EventType
import com.blitztech.pudokiosk.data.events.Outbox
import com.blitztech.pudokiosk.data.repo.CourierRepository
import com.blitztech.pudokiosk.data.sync.WorkScheduler
import com.blitztech.pudokiosk.deviceio.rs232.BarcodeScanner1900
import com.blitztech.pudokiosk.deviceio.rs485.LockerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.blitztech.pudokiosk.data.AuditLogger
import com.blitztech.pudokiosk.prefs.Prefs

class CourierActivity : AppCompatActivity() {

    private lateinit var stepLogin: View
    private lateinit var etCode: EditText
    private lateinit var btnLogin: Button

    private lateinit var stepWork: View
    private lateinit var tvCourier: TextView
    private lateinit var tvItem: TextView
    private lateinit var tvScanStatus: TextView
    private lateinit var btnOpen: Button
    private lateinit var btnConfirm: Button
    private lateinit var btnDone: Button
    private lateinit var prefs: Prefs

    private val repo = CourierRepository(useStub = true)
    private lateinit var locker: LockerController
    private lateinit var scanner: BarcodeScanner1900

    private var reminder: LockerReminder? = null
    private var simulateHardware = true
    private var scanJob: Job? = null

    private var courierId: String? = null
    private var work: MutableList<com.blitztech.pudokiosk.data.net.CourierParcel> = mutableListOf()
    private var idx = 0
    private var lastScan: String? = null
    private var locale = "en"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_courier)

        prefs = Prefs(this)
        locker = LockerController(this, simulate = prefs.isSimHardware())
        scanner = BarcodeScanner1900(this, preferredBaud = prefs.getScannerBaud())

        stepLogin = findViewById(R.id.stepLogin)
        etCode = findViewById(R.id.etCode)
        btnLogin = findViewById(R.id.btnLogin)

        stepWork = findViewById(R.id.stepWork)
        tvCourier = findViewById(R.id.tvCourier)
        tvItem = findViewById(R.id.tvItem)
        tvScanStatus = findViewById(R.id.tvScanStatus)
        btnOpen = findViewById(R.id.btnOpen)
        btnConfirm = findViewById(R.id.btnConfirm)
        btnDone = findViewById(R.id.btnDone)

        btnLogin.setOnClickListener { onLogin() }
        btnOpen.setOnClickListener { onOpen() }
        btnConfirm.setOnClickListener { onConfirm() }
        btnDone.setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        // Start scanner; we’ll listen after login.
        scanner.start()
    }

    override fun onStop() {
        super.onStop()
        scanJob?.cancel()
        scanner.stop()
        reminder?.stop()
    }

    private fun onLogin() {
        val code = etCode.text.toString().trim()
        if (code.isEmpty()) { toast("Scan badge or enter PIN"); return }
        lifecycleScope.launch {
            val r = repo.login(code)
            courierId = r.courierId
            tvCourier.text = "Hello, ${r.name}"
            // fetch worklist
            work.clear()
            work.addAll(repo.listToCollect(r.courierId))
            idx = 0
            if (work.isEmpty()) { toast("No items to collect"); return@launch }
            stepLogin.visibility = View.GONE
            stepWork.visibility = View.VISIBLE
            updateItemUI()
            // start listening for scans now
            scanJob?.cancel()
            scanJob = lifecycleScope.launch {
                scanner.scans.collectLatest { code ->
                    lastScan = code
                    val expected = current()?.tracking ?: ""
                    val ok = code.equals(expected, ignoreCase = true)
                    tvScanStatus.text = if (ok) "Scanned OK: $code" else "Scanned $code (expected $expected)"
                    btnConfirm.isEnabled = ok
                }
            }
        }
    }

    private fun current(): com.blitztech.pudokiosk.data.net.CourierParcel? =
        if (idx in work.indices) work[idx] else null

    private fun updateItemUI() {
        val c = current()
        tvItem.text = c?.let { "Next: ${it.tracking} (${it.size}) at ${it.lockerId}" } ?: "All done"
        tvScanStatus.text = "Waiting for scan..."
        btnConfirm.isEnabled = false
    }

    private fun onOpen() {
        val c = current() ?: return
        lifecycleScope.launch {
            Outbox.enqueue(EventType.LOCKER_OPEN_REQUEST, mapOf("lockerId" to c.lockerId, "parcelId" to c.parcelId))
            WorkScheduler.flushOutboxNow(this@CourierActivity)

            val ok = locker.openLocker(c.lockerId, retries = 2)
            if (ok) {
                AuditLogger.log("INFO", "LOCKER_OPEN_SUCCESS", "lockerId=${c.lockerId} parcelId=${c.parcelId}")
                Outbox.enqueue(EventType.LOCKER_OPEN_SUCCESS, mapOf("lockerId" to c.lockerId, "parcelId" to c.parcelId))
                WorkScheduler.flushOutboxNow(this@CourierActivity)
                reminder?.stop()
                reminder = LockerReminder(this@CourierActivity, locale).also { it.start() }
                toast("Locker opened. Remove parcel and scan its ticket.")
            } else {
                AuditLogger.log("ERROR", "LOCKER_OPEN_FAIL", "lockerId=${c.lockerId} parcelId=${c.parcelId} reason=NoACK")
                Outbox.enqueue(EventType.LOCKER_OPEN_FAIL, mapOf("lockerId" to c.lockerId, "parcelId" to c.parcelId, "reason" to "No ACK"))
                WorkScheduler.flushOutboxNow(this@CourierActivity)
                toast("Failed to open locker.")
            }
        }
    }

    private fun onConfirm() {
        val c = current() ?: return
        lifecycleScope.launch {
            if (!locker.isClosed(c.lockerId)) {
                toast("Door still open. Please close it.")
                return@launch
            }
            reminder?.stop()
            try {
                repo.markCollected(c.parcelId)
                AuditLogger.log("INFO", "COURIER_COLLECTED", "parcelId=${c.parcelId} lockerId=${c.lockerId}")
                toast("Collected ${c.tracking}")
            } catch (e: Exception) {
                AuditLogger.log("ERROR", "COURIER_MARK_COLLECTED_FAIL", "parcelId=${c.parcelId} msg=${e.message}")
                toast("Failed to confirm collection")
                return@launch
            }
            idx += 1
            if (idx >= work.size) {
                toast("All done!")
                finish()
            } else {
                updateItemUI()
            }
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
