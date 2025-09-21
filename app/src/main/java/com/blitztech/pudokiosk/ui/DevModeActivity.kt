package com.blitztech.pudokiosk.ui

import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.data.ServiceLocator
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent


class DevModeActivity : AppCompatActivity() {

    private lateinit var tvOutbox: TextView
    private lateinit var tvUsb: TextView
    private lateinit var tvNet: TextView
    private lateinit var tvAudit: TextView
    private lateinit var btnHardwareTest: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnSettings: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_mode)

        tvOutbox = findViewById(R.id.tvOutbox)
        tvUsb = findViewById(R.id.tvUsb)
        tvNet = findViewById(R.id.tvNet)
        tvAudit = findViewById(R.id.tvAudit)
        btnRefresh = findViewById(R.id.btnRefresh)

        btnRefresh.setOnClickListener { updateUi() }
        btnSettings = findViewById(R.id.btnSettings)
        btnSettings.setOnClickListener {
            startActivity(Intent(this, DevSettingsActivity::class.java))
        }

        btnHardwareTest = findViewById(R.id.btnHardwareTest)
        btnHardwareTest.setOnClickListener {
            startActivity(Intent(this, HardwareTestActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun updateUi() {
        // Outbox queue depth
        lifecycleScope.launch {
            try {
                val pending = ServiceLocator.db.outbox().pending(200).size
                tvOutbox.text = "Outbox pending: $pending"
            } catch (e: Exception) {
                // If your DAO doesn't have pending(limit): fall back to a count() query or adjust DAO.
                tvOutbox.text = "Outbox pending: (unknown)"
            }
        }

        // USB devices
        val usb = getSystemService(USB_SERVICE) as UsbManager
        val names = usb.deviceList.values.joinToString { "${it.vendorId}:${it.productId}" }
        tvUsb.text = "USB: ${if (names.isEmpty()) "none" else names}"

        // Backend ping
        Thread {
            try {
                val client = OkHttpClient()
                val req = Request.Builder().url("https://example.com/health").head().build() // TODO: your real health URL
                val resp = client.newCall(req).execute()
                runOnUiThread { tvNet.text = "Backend: ${resp.code}" }
            } catch (_: Exception) {
                runOnUiThread { tvNet.text = "Backend: FAIL" }
            }
        }.start()

        // Recent audit
        lifecycleScope.launch {
            try {
                val logs = ServiceLocator.db.audit().recent(20)
                val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
                tvAudit.text = logs.joinToString("\n") {
                    "${fmt.format(Date(it.ts))} ${it.level} ${it.event}${it.details?.let { d -> " [$d]" } ?: ""}"
                }
            } catch (e: Exception) {
                tvAudit.text = "No audit entries yet."
            }
        }
    }
}
