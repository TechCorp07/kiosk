package com.blitztech.pudokiosk.ui.Technician

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity

class DevSettingsActivity : BaseKioskActivity() {
    private lateinit var prefs: Prefs
    private lateinit var chkSimHw: CheckBox
    private lateinit var chkIm30: CheckBox
    private lateinit var etScannerBaud: EditText
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_settings)

        prefs = Prefs(this)
        chkSimHw = findViewById(R.id.chkSimHw)
        chkIm30 = findViewById(R.id.chkIm30)
        etScannerBaud = findViewById(R.id.etScannerBaud)
        btnSave = findViewById(R.id.btnSave)

        // Load
        etScannerBaud.setText(prefs.getScannerBaud().toString())

        btnSave.setOnClickListener {
            val baud = etScannerBaud.text.toString().toIntOrNull() ?: 115200
            prefs.setScannerBaud(baud)
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
