package com.blitztech.pudokiosk.ui.technician

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.databinding.ActivityTechnicianMenuBinding
import com.blitztech.pudokiosk.service.KioskLockManager
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.blitztech.pudokiosk.ui.main.MainActivity
import com.blitztech.pudokiosk.update.AppUpdateManager
import com.blitztech.pudokiosk.update.UpdateCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Technician menu providing access to diagnostic tools, logs, settings,
 * maintenance mode toggle, and OTA update checks.
 */
class TechnicianMenuActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityTechnicianMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTechnicianMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Disable kiosk mode for the technician session
        disableKioskMode()

        setupViews()
        setupClickListeners()
    }

    private fun setupViews() {
        binding.tvTitle.text = "Technician Diagnostics"
        binding.tvSubtitle.text = "Select diagnostic tool or setting"
        updateMaintenanceButton()
        updateVersionInfo()
    }

    private fun updateMaintenanceButton() {
        val inMaintenance = ZimpudoApp.prefs.isMaintenanceMode()
        if (inMaintenance) {
            binding.tvMaintenanceLabel.text = "Exit Maintenance"
            binding.cardMaintenance.setCardBackgroundColor(
                resources.getColor(com.blitztech.pudokiosk.R.color.warning_light, theme)
            )
        } else {
            binding.tvMaintenanceLabel.text = "Maintenance Mode"
            binding.cardMaintenance.setCardBackgroundColor(
                resources.getColor(com.blitztech.pudokiosk.R.color.white, theme)
            )
        }
    }

    private fun updateVersionInfo() {
        val versionName = AppUpdateManager.getAppVersionName(this)
        binding.tvVersionInfo.text = "App Version: $versionName"
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

        // Developer Mode
        binding.cardDevMode.setOnClickListener {
            startActivity(Intent(this, DeveloperModeActivity::class.java))
        }

        // Maintenance Mode toggle
        binding.cardMaintenance.setOnClickListener {
            toggleMaintenanceMode()
        }

        // Check for Updates
        binding.cardCheckUpdate.setOnClickListener {
            checkForUpdates()
        }
    }

    private fun toggleMaintenanceMode() {
        val currentlyInMaintenance = ZimpudoApp.prefs.isMaintenanceMode()

        if (currentlyInMaintenance) {
            // Exiting maintenance mode — re-lock the kiosk
            KioskLockManager.setMaintenanceMode(this, false)
            Toast.makeText(this, "Maintenance mode OFF — Kiosk will re-lock", Toast.LENGTH_LONG).show()
        } else {
            // Entering maintenance mode — unlock the kiosk
            KioskLockManager.setMaintenanceMode(this, true)
            Toast.makeText(this, "Maintenance mode ON — You can now access the system", Toast.LENGTH_LONG).show()
        }

        updateMaintenanceButton()
    }

    private fun checkForUpdates() {
        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val updateInfo = withContext(Dispatchers.IO) {
                    AppUpdateManager.checkForUpdate(this@TechnicianMenuActivity)
                }

                if (updateInfo != null) {
                    Toast.makeText(
                        this@TechnicianMenuActivity,
                        "Update available: v${updateInfo.versionName}\nDownloading...",
                        Toast.LENGTH_LONG
                    ).show()

                    val success = AppUpdateManager.downloadAndInstall(
                        this@TechnicianMenuActivity, updateInfo
                    )

                    if (!success) {
                        Toast.makeText(
                            this@TechnicianMenuActivity,
                            "Update download failed. Please try again.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@TechnicianMenuActivity,
                        "App is up to date!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@TechnicianMenuActivity,
                    "Update check failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun returnToMainApp() {
        // Ensure maintenance mode is off when leaving tech menu
        // (unless technician explicitly left it on)
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finishSafely()
    }
}