package com.blitztech.pudokiosk.ui.main

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.audio.LockerReminder
import com.blitztech.pudokiosk.data.repository.CourierRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.blitztech.pudokiosk.data.AuditLogger
import com.blitztech.pudokiosk.data.net.CourierParcel
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

    private var reminder: LockerReminder? = null
    private var scanJob: Job? = null

    private var courierId: String? = null
    private var work: MutableList<CourierParcel> = mutableListOf()
    private var idx = 0
    private var lastScan: String? = null
    private var locale = "en"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_courier)

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
        btnConfirm.setOnClickListener { onConfirm() }
        btnDone.setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
        scanJob?.cancel()
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
        }
    }

    private fun current(): CourierParcel? =
        if (idx in work.indices) work[idx] else null

    private fun updateItemUI() {
        val c = current()
        tvItem.text = c?.let { "Next: ${it.tracking} (${it.size}) at ${it.lockerId}" } ?: "All done"
        tvScanStatus.text = "Waiting for scan..."
        btnConfirm.isEnabled = false
    }


    private fun onConfirm() {
        val c = current() ?: return
        lifecycleScope.launch {
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
