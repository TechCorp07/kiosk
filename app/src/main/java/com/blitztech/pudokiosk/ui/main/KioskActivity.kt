package com.blitztech.pudokiosk.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.databinding.ActivityKioskBinding
import com.blitztech.pudokiosk.i18n.I18n
import com.blitztech.pudokiosk.service.DeviceService
import android.app.AlertDialog
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.data.ServiceLocator
import com.blitztech.pudokiosk.ui.DevModeActivity
import kotlinx.coroutines.launch
import org.json.JSONObject

class KioskActivity : AppCompatActivity() {
    private lateinit var binding: ActivityKioskBinding
    private lateinit var i18n: I18n
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKioskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // start foreground service
        DeviceService.start(this)

        // init prefs + i18n once
        prefs = Prefs(this)
        i18n = I18n(this)

        // 1) pick initial locale: prefs -> device fallback
        val initialLocale = prefs.getLocale().ifBlank { I18n.localeFromDevice() }
        i18n.load(initialLocale)
        applyTexts()

        // 2) (optional) override from remote config if present
        lifecycleScope.launch {
            try {
                val cfg = ServiceLocator.config.local() // suspend call
                val json = cfg?.json
                if (!json.isNullOrBlank()) {
                    val obj = JSONObject(json)
                    val remoteLocale = obj.optString("locale", "")
                    if (remoteLocale.isNotBlank() && remoteLocale != prefs.getLocale()) {
                        prefs.setLocale(remoteLocale)
                        i18n.load(remoteLocale)
                        applyTexts()
                    }
                }
            } catch (_: Exception) {
                // if config not ready yet, ignore
            }
        }

        // language picker (long-press title)
        binding.title.setOnLongClickListener {
            val langs = arrayOf("English (en)", "Shona (sn)", "isiNdebele (nd)")
            val codes = arrayOf("en","sn","nd")
            val idx = codes.indexOf(prefs.getLocale()).coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setTitle("Choose language")
                .setSingleChoiceItems(langs, idx) { d, which ->
                    val code = codes[which]
                    prefs.setLocale(code)
                    i18n.load(code)
                    applyTexts()
                    d.dismiss()
                }
                .show()
            true
        }

        // nav buttons
        binding.btnAdmin.setOnClickListener {
            startActivity(Intent(this, DevModeActivity::class.java))
        }
        binding.btnSend.setOnClickListener {
            startActivity(Intent(this, SenderActivity::class.java))
        }
        binding.btnCollect.setOnClickListener {
            startActivity(Intent(this, RecipientActivity::class.java))
        }
        binding.btnCourier.setOnClickListener {
            startActivity(Intent(this, CourierActivity::class.java))
        }
    }

    private fun applyTexts() {
        binding.btnSend.text = i18n.t("welcome.send")
        binding.btnCollect.text = i18n.t("welcome.collect")
        binding.btnCourier.text = i18n.t("welcome.courier")
        binding.btnAdmin.text = i18n.t("admin.devmode")
    }
}
