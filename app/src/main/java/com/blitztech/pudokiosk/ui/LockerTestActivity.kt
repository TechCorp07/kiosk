package com.blitztech.pudokiosk.deviceio.rs485

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Test Activity for STM32L412 Locker Controller
 * Provides UI for testing locker operations and system diagnostics
 */
class LockerTestActivity : AppCompatActivity() {

    private lateinit var lockerController: LockerController
    private lateinit var etLockerId: EditText
    private lateinit var spStation: Spinner
    private lateinit var spLockNumber: Spinner
    private lateinit var tvStatus: TextView
    private lateinit var tvSystemStatus: TextView
    private lateinit var btnOpenLocker: Button
    private lateinit var btnCheckStatus: Button
    private lateinit var btnTestStation: Button
    private lateinit var btnSystemDiagnostics: Button
    private lateinit var swSimulateMode: Switch
    private lateinit var progressBar: ProgressBar

    companion object {
        private const val TAG = "LockerTestActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createLayout())

        initializeViews()
        setupSpinners()
        setupEventListeners()

        // Initialize controller in simulation mode by default
        lockerController = LockerController(this, simulate = true)

        updateStatusText("Initialized in simulation mode")
    }

    private fun createLayout(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        layout.addView(TextView(this).apply {
            text = "🔐 STM32L412 Locker Controller Test"
            textSize = 20f
            setPadding(0, 0, 0, 24)
        })

        // Simulation mode toggle
        layout.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(this@LockerTestActivity).apply {
                text = "Simulation Mode:"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Switch(this@LockerTestActivity).apply {
                swSimulateMode = this
                isChecked = true
            })
        })

        // Locker ID input
        layout.addView(TextView(this).apply { text = "Locker ID (e.g., M12):" })
        layout.addView(EditText(this).apply {
            etLockerId = this
            hint = "M12"
            setText("M1")
        })

        // Station selection
        layout.addView(TextView(this).apply { text = "Station (for direct testing):" })
        layout.addView(Spinner(this).apply { spStation = this })

        // Lock number selection
        layout.addView(TextView(this).apply { text = "Lock Number (for direct testing):" })
        layout.addView(Spinner(this).apply { spLockNumber = this })

        // Action buttons
        layout.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(this@LockerTestActivity).apply {
                btnOpenLocker = this
                text = "Open Locker"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Button(this@LockerTestActivity).apply {
                btnCheckStatus = this
                text = "Check Status"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        })

        layout.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(this@LockerTestActivity).apply {
                btnTestStation = this
                text = "Test Station"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Button(this@LockerTestActivity).apply {
                btnSystemDiagnostics = this
                text = "System Diagnostics"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        })

        // Progress bar
        layout.addView(ProgressBar(this).apply {
            progressBar = this
            visibility = ProgressBar.GONE
        })

        // Status display
        layout.addView(TextView(this).apply { text = "Status:" })
        layout.addView(TextView(this).apply {
            tvStatus = this
            text = "Ready"
            setBackgroundColor(0xFFE8F5E8.toInt())
            setPadding(16, 16, 16, 16)
        })

        // System status display
        layout.addView(TextView(this).apply { text = "System Status:" })
        layout.addView(ScrollView(this).apply {
            addView(TextView(this@LockerTestActivity).apply {
                tvSystemStatus = this
                text = "Not checked yet"
                setBackgroundColor(0xFFF5F5F5.toInt())
                setPadding(16, 16, 16, 16)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    300
                )
            })
        })

        return layout
    }

    private fun initializeViews() {
        // Views are already initialized in createLayout()
    }

    private fun setupSpinners() {
        // Station spinner (0-3)
        val stationAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            listOf("Station 0 (DIP: 00)", "Station 1 (DIP: 01)", "Station 2 (DIP: 10)", "Station 3 (DIP: 11)"))
        stationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spStation.adapter = stationAdapter

        // Lock number spinner (1-16)
        val lockAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            (1..16).map { "Lock $it" })
        lockAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spLockNumber.adapter = lockAdapter
    }

    private fun setupEventListeners() {
        swSimulateMode.setOnCheckedChangeListener { _, isChecked ->
            lockerController = LockerController(this, simulate = isChecked)
            updateStatusText("Switched to ${if (isChecked) "simulation" else "hardware"} mode")
        }

        btnOpenLocker.setOnClickListener {
            val lockerId = etLockerId.text.toString().trim()
            if (lockerId.isNotEmpty()) {
                openLocker(lockerId)
            } else {
                updateStatusText("Please enter a locker ID")
            }
        }

        btnCheckStatus.setOnClickListener {
            val lockerId = etLockerId.text.toString().trim()
            if (lockerId.isNotEmpty()) {
                checkLockerStatus(lockerId)
            } else {
                updateStatusText("Please enter a locker ID")
            }
        }

        btnTestStation.setOnClickListener {
            val station = spStation.selectedItemPosition
            testStation(station)
        }

        btnSystemDiagnostics.setOnClickListener {
            runSystemDiagnostics()
        }
    }

    private fun openLocker(lockerId: String) {
        showProgress(true)
        updateStatusText("Opening locker $lockerId...")

        lifecycleScope.launch {
            try {
                val success = lockerController.openLocker(lockerId)
                val mapping = lockerController.getLockerMapping(lockerId)

                if (success) {
                    updateStatusText("✅ Successfully opened $lockerId\n$mapping")
                } else {
                    updateStatusText("❌ Failed to open $lockerId\n$mapping")
                }
            } catch (e: Exception) {
                updateStatusText("❌ Error opening $lockerId: ${e.message}")
                Log.e(TAG, "Error opening locker", e)
            } finally {
                showProgress(false)
            }
        }
    }

    private fun checkLockerStatus(lockerId: String) {
        showProgress(true)
        updateStatusText("Checking status of locker $lockerId...")

        lifecycleScope.launch {
            try {
                val isClosed = lockerController.isClosed(lockerId)
                val mapping = lockerController.getLockerMapping(lockerId)
                val status = if (isClosed) "CLOSED" else "OPEN"

                updateStatusText("📋 Locker $lockerId is $status\n$mapping")
            } catch (e: Exception) {
                updateStatusText("❌ Error checking $lockerId status: ${e.message}")
                Log.e(TAG, "Error checking locker status", e)
            } finally {
                showProgress(false)
            }
        }
    }

    private fun testStation(station: Int) {
        showProgress(true)
        updateStatusText("Testing station $station...")

        lifecycleScope.launch {
            try {
                val success = lockerController.testStation(station)
                val dipSetting = when (station) {
                    0 -> "00"
                    1 -> "01"
                    2 -> "10"
                    3 -> "11"
                    else -> "??"
                }

                if (success) {
                    updateStatusText("✅ Station $station (DIP: $dipSetting) is ONLINE")
                } else {
                    updateStatusText("❌ Station $station (DIP: $dipSetting) is OFFLINE")
                }
            } catch (e: Exception) {
                updateStatusText("❌ Error testing station $station: ${e.message}")
                Log.e(TAG, "Error testing station", e)
            } finally {
                showProgress(false)
            }
        }
    }

    private fun runSystemDiagnostics() {
        showProgress(true)
        updateStatusText("Running system diagnostics...")

        lifecycleScope.launch {
            try {
                val systemStatus = lockerController.getSystemStatus()

                val statusBuilder = StringBuilder().apply {
                    appendLine("🔧 SYSTEM DIAGNOSTICS REPORT")
                    appendLine("=" * 30)
                    appendLine("Total Stations: 4")
                    appendLine("Expected Capacity: 64 lockers")
                    appendLine()

                    systemStatus.forEach { (station, isOnline) ->
                        val dipSetting = when (station) {
                            0 -> "00"
                            1 -> "01"
                            2 -> "10"
                            3 -> "11"
                            else -> "??"
                        }
                        val status = if (isOnline) "✅ ONLINE" else "❌ OFFLINE"
                        appendLine("Station $station (DIP: $dipSetting): $status")

                        if (isOnline) {
                            val lockerRange = "${station * 16 + 1}-${(station + 1) * 16}"
                            appendLine("  → Controls lockers M$lockerRange")
                        }
                    }

                    appendLine()
                    val onlineCount = systemStatus.values.count { it }
                    appendLine("Summary: $onlineCount/4 stations online")

                    if (onlineCount == 4) {
                        appendLine("🟢 All systems operational!")
                    } else {
                        appendLine("🟡 Some stations offline - check connections")
                    }

                    appendLine()
                    appendLine("Configuration:")
                    appendLine("• Protocol: Winnsen Custom over RS485")
                    appendLine("• Baud Rate: 9600")
                    appendLine("• Data Format: 8N1")
                    appendLine("• Locks per Board: 16")
                    appendLine("• Simulation Mode: ${swSimulateMode.isChecked}")
                }

                tvSystemStatus.text = statusBuilder.toString()
                updateStatusText("System diagnostics completed")

            } catch (e: Exception) {
                updateStatusText("❌ Error running diagnostics: ${e.message}")
                Log.e(TAG, "Error running diagnostics", e)
            } finally {
                showProgress(false)
            }
        }
    }

    private fun updateStatusText(text: String) {
        runOnUiThread {
            tvStatus.text = "$text\n\nTime: ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}"
        }
    }

    private fun showProgress(show: Boolean) {
        runOnUiThread {
            progressBar.visibility = if (show) ProgressBar.VISIBLE else ProgressBar.GONE
            btnOpenLocker.isEnabled = !show
            btnCheckStatus.isEnabled = !show
            btnTestStation.isEnabled = !show
            btnSystemDiagnostics.isEnabled = !show
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            lockerController.close()
        }
    }
}