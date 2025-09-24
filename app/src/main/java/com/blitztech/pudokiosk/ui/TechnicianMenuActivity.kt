package com.blitztech.pudokiosk.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.blitztech.pudokiosk.databinding.ActivityTechnicianMenuBinding
import com.blitztech.pudokiosk.ui.main.MainActivity

/**
 * Technician menu providing access to diagnostic tools, logs, and settings
 */
class TechnicianMenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTechnicianMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTechnicianMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        setupClickListeners()
    }

    private fun setupViews() {
        binding.tvTitle.text = "Technician Diagnostics"
        binding.tvSubtitle.text = "Select diagnostic tool or setting"
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            returnToMainApp()
        }

        binding.btnLogout.setOnClickListener {
            returnToMainApp()
        }

        // Hardware Test Activity
        binding.cardHardwareTest.setOnClickListener {
            startActivity(Intent(this, HardwareTestActivity::class.java))
        }

        // System Logs
        binding.cardSystemLogs.setOnClickListener {
            startActivity(Intent(this, SystemLogsActivity::class.java))
        }

        // Device Settings
        binding.cardDeviceSettings.setOnClickListener {
            startActivity(Intent(this, DevSettingsActivity::class.java))
        }

        // Network Diagnostics
        binding.cardNetworkDiag.setOnClickListener {
            startActivity(Intent(this, NetworkDiagnosticsActivity::class.java))
        }

        // System Info
        binding.cardSystemInfo.setOnClickListener {
            startActivity(Intent(this, SystemInfoActivity::class.java))
        }

        // Clear Data/Cache
        binding.cardClearData.setOnClickListener {
            startActivity(Intent(this, DataManagementActivity::class.java))
        }

        // Remote Support
        binding.cardRemoteSupport.setOnClickListener {
            startActivity(Intent(this, RemoteSupportActivity::class.java))
        }
    }

    private fun returnToMainApp() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        returnToMainApp()
    }
}