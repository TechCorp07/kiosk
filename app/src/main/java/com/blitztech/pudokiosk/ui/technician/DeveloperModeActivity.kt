package com.blitztech.pudokiosk.ui.technician

import com.blitztech.pudokiosk.R

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import com.blitztech.pudokiosk.databinding.ActivityDeveloperModeBinding
import com.blitztech.pudokiosk.prefs.Prefs
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Developer Mode Activity — advanced configuration panel for developers and senior technicians.
 *
 * Sections:
 * 1. Feature Flags   — Toggle hardware simulation, verbose logging, mock API, OTP bypass
 * 2. Environment     — Switch backend between production / staging / local / custom URL
 * 3. Log Level       — Set runtime log verbosity persisted to Prefs
 * 4. Perf Monitor    — Real-time heap, thread count, GC stats
 * 5. Build Info      — Show build variant, SDK version, compile date
 */
class DeveloperModeActivity : BaseKioskActivity() {

    companion object {
        // Prefs keys specific to Developer Mode
        const val KEY_SIM_HARDWARE    = "dev_sim_hardware"
        const val KEY_DEBUG_LOGGING   = "dev_debug_logging"
        const val KEY_MOCK_API        = "dev_mock_api"
        const val KEY_MOCK_LOCATION   = "dev_mock_location"
        const val KEY_SKIP_OTP        = "dev_skip_otp"
        const val KEY_ENVIRONMENT     = "dev_environment"   // "prod" | "staging" | "local" | "custom"
        const val KEY_CUSTOM_URL      = "dev_custom_url"
        const val KEY_LOG_LEVEL       = "dev_log_level"

        // Environment identifiers
        const val ENV_PRODUCTION = "prod"
        const val ENV_STAGING    = "staging"
        const val ENV_LOCAL      = "local"
        const val ENV_CUSTOM     = "custom"
    }

    private lateinit var binding: ActivityDeveloperModeBinding
    private lateinit var prefs: Prefs
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private val logLevels = listOf("VERBOSE", "DEBUG", "INFO", "WARN", "ERROR")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeveloperModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = ZimpudoApp.prefs

        setupLogLevelSpinner()
        loadSavedSettings()
        setupBuildInfo()
        setupClickListeners()
        refreshPerformanceStats()
    }

    // ─── Setup ───────────────────────────────────────────────────────────────

    private fun setupLogLevelSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, logLevels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLogLevel.adapter = adapter

        val savedLevel = prefs.getString(KEY_LOG_LEVEL, "INFO")
        val index = logLevels.indexOf(savedLevel).coerceAtLeast(0)
        binding.spinnerLogLevel.setSelection(index)
    }

    private fun loadSavedSettings() {
        // Feature flags
        binding.chkSimulateHardware.isChecked = prefs.getBoolean(KEY_SIM_HARDWARE, false)
        binding.chkDebugLogging.isChecked     = prefs.getBoolean(KEY_DEBUG_LOGGING, false)
        binding.chkMockApi.isChecked          = prefs.getBoolean(KEY_MOCK_API, false)
        binding.chkMockLocation.isChecked     = prefs.getBoolean(KEY_MOCK_LOCATION, false)
        binding.chkSkipOtp.isChecked          = prefs.getBoolean(KEY_SKIP_OTP, false)

        // Environment
        when (prefs.getString(KEY_ENVIRONMENT, ENV_PRODUCTION)) {
            ENV_STAGING -> binding.rbEnvStaging.isChecked = true
            ENV_LOCAL   -> binding.rbEnvLocal.isChecked   = true
            ENV_CUSTOM  -> { /* radio group defaults to prod, user sees custom url */ }
            else        -> binding.rbEnvProduction.isChecked = true
        }

        // Custom URL
        binding.etCustomUrl.setText(prefs.getString(KEY_CUSTOM_URL, ""))

        // Log level — relies on spinner already having adapter
        val savedLevel = prefs.getString(KEY_LOG_LEVEL, "INFO")
        binding.tvCurrentLogLevel.text = "Current: $savedLevel"
    }

    private fun setupBuildInfo() {
        val buildInfo = buildString {
            appendLine("Version Name : 0.1.0")
            appendLine("Build Type  : ${Build.TYPE}")
            appendLine("Host SDK    : ${Build.VERSION.SDK_INT} (Android ${Build.VERSION.RELEASE})")
            appendLine("Device      : ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Board       : ${Build.BOARD}")
            appendLine("CPU ABI     : ${Build.CPU_ABI}")
            appendLine("Build ID    : ${Build.ID}")
            appendLine("Build Date  : ${dateFormatter.format(Date())}")
        }
        binding.tvBuildInfo.text = buildInfo
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            returnToTechMenu()
        }

        binding.btnSaveSettings.setOnClickListener {
            saveSettings()
        }

        binding.btnRefreshStats.setOnClickListener {
            refreshPerformanceStats()
        }
    }

    // ─── Save / Load ──────────────────────────────────────────────────────────

    private fun saveSettings() {
        // Feature flags
        prefs.putBoolean(KEY_SIM_HARDWARE,  binding.chkSimulateHardware.isChecked)
        prefs.putBoolean(KEY_DEBUG_LOGGING, binding.chkDebugLogging.isChecked)
        prefs.putBoolean(KEY_MOCK_API,      binding.chkMockApi.isChecked)
        prefs.putBoolean(KEY_MOCK_LOCATION, binding.chkMockLocation.isChecked)
        prefs.putBoolean(KEY_SKIP_OTP,      binding.chkSkipOtp.isChecked)

        // Environment
        val env = when {
            binding.rbEnvStaging.isChecked -> ENV_STAGING
            binding.rbEnvLocal.isChecked   -> ENV_LOCAL
            else                           -> ENV_PRODUCTION
        }
        prefs.putString(KEY_ENVIRONMENT, env)

        // Custom URL
        val customUrl = binding.etCustomUrl.text?.toString()?.trim() ?: ""
        prefs.putString(KEY_CUSTOM_URL, customUrl)

        // Log level
        val selectedLevel = binding.spinnerLogLevel.selectedItem?.toString() ?: "INFO"
        prefs.putString(KEY_LOG_LEVEL, selectedLevel)
        binding.tvCurrentLogLevel.text = "Current: $selectedLevel"

        val savedAt = dateFormatter.format(Date())
        binding.tvLastSaved.text = "Last saved: $savedAt"

        Toast.makeText(this, getString(R.string.auto_rem_developer_settings_saved), Toast.LENGTH_SHORT).show()
    }

    // ─── Performance Stats ───────────────────────────────────────────────────

    private fun refreshPerformanceStats() {
        val runtime = Runtime.getRuntime()
        val usedMemMb  = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576L
        val freeMemMb  = runtime.freeMemory()  / 1_048_576L
        val maxMemMb   = runtime.maxMemory()   / 1_048_576L
        val totalMemMb = runtime.totalMemory() / 1_048_576L
        val threadCount = Thread.activeCount()

        val stats = buildString {
            appendLine("Heap Used   : ${usedMemMb} MB")
            appendLine("Heap Free   : ${freeMemMb} MB")
            appendLine("Heap Total  : ${totalMemMb} MB")
            appendLine("Heap Max    : ${maxMemMb} MB")
            appendLine("Threads     : $threadCount active")
            appendLine("Processors  : ${runtime.availableProcessors()} cores")
            appendLine("Refreshed   : ${dateFormatter.format(Date())}")
        }
        binding.tvPerfStats.text = stats
    }

    // ─── Navigation ──────────────────────────────────────────────────────────

    private fun returnToTechMenu() {
        finishSafely()
    }
}
