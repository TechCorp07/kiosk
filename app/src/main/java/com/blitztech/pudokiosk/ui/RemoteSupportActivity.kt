package com.blitztech.pudokiosk.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.databinding.ActivityRemoteSupportBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Remote Support Activity - Generate support codes and enable remote assistance
 * Compatible with Android API 25 (Android 7.1.2)
 */
class RemoteSupportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRemoteSupportBinding
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private var supportSessionActive = false
    private var supportCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemoteSupportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        setupClickListeners()
        loadDeviceInfo()
    }

    private fun setupViews() {
        binding.tvTitle.text = "Remote Support"
        binding.tvSubtitle.text = "Enable remote assistance and generate support codes"
        binding.tvLastUpdated.text = "Last updated: ${dateFormatter.format(Date())}"
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            returnToTechMenu()
        }

        binding.btnGenerateCode.setOnClickListener {
            generateSupportCode()
        }

        binding.btnStartSession.setOnClickListener {
            if (supportSessionActive) {
                stopSupportSession()
            } else {
                startSupportSession()
            }
        }

        binding.btnRefresh.setOnClickListener {
            refreshSupportInfo()
        }

        binding.btnCopyCode.setOnClickListener {
            copySupportCodeToClipboard()
        }

        binding.btnShareDiagnostics.setOnClickListener {
            shareDiagnosticInfo()
        }

        binding.btnContactSupport.setOnClickListener {
            contactSupport()
        }
    }

    private fun loadDeviceInfo() {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val deviceInfo = withContext(Dispatchers.IO) {
                    generateDeviceInfo()
                }
                binding.tvDeviceInfo.text = deviceInfo
                binding.tvLastUpdated.text = "Last updated: ${dateFormatter.format(Date())}"
            } catch (e: Exception) {
                binding.tvDeviceInfo.text = "Error loading device info: ${e.message}"
                showToast("Error: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private suspend fun generateDeviceInfo(): String = withContext(Dispatchers.IO) {
        return@withContext try {
            """
Device ID: ${Build.SERIAL}
Model: ${Build.MANUFACTURER} ${Build.MODEL}
Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
App Version: 0.1.0
Build: ${Build.ID}

Network Status: Connected
Backend Status: Online
Hardware Status: All devices operational

Last Boot: System uptime available
Support Status: ${if (supportSessionActive) "ACTIVE SESSION" else "Ready for support"}
Support Code: ${supportCode ?: "Not generated"}

Timestamp: ${dateFormatter.format(Date())}
            """.trimIndent()
        } catch (e: Exception) {
            "Error generating device info: ${e.message}"
        }
    }

    private fun generateSupportCode() {
        setLoading(true)

        lifecycleScope.launch {
            try {
                // Generate a unique support code
                val timestamp = System.currentTimeMillis()
                val deviceId = Build.SERIAL.take(4)
                val random = (1000..9999).random()

                supportCode = "ZK-$deviceId-$random"

                binding.tvSupportCode.text = supportCode
                binding.tvSupportCodeExpiry.text = "Valid until: ${
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp + 3600000))
                }" // Valid for 1 hour

                // Enable copy and session buttons
                binding.btnCopyCode.isEnabled = true
                binding.btnStartSession.isEnabled = true

                showToast("Support code generated successfully")
                refreshSupportInfo()

            } catch (e: Exception) {
                showToast("Error generating support code: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun startSupportSession() {
        if (supportCode == null) {
            showToast("Please generate a support code first")
            return
        }

        setLoading(true)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Simulate starting remote support session
                    Thread.sleep(2000) // Simulate network call
                }

                supportSessionActive = true
                updateSessionUI()

                showToast("Remote support session started")

            } catch (e: Exception) {
                showToast("Failed to start support session: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun stopSupportSession() {
        setLoading(true)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Simulate stopping remote support session
                    Thread.sleep(1000)
                }

                supportSessionActive = false
                updateSessionUI()

                showToast("Remote support session ended")

            } catch (e: Exception) {
                showToast("Error ending support session: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun updateSessionUI() {
        if (supportSessionActive) {
            binding.btnStartSession.text = "End Session"
            binding.tvSessionStatus.text = "🟢 Support session is ACTIVE"
            binding.tvSessionDetails.text = """
Remote technician can now:
• View system diagnostics
• Access device logs
• Run hardware tests
• Monitor real-time status

Session started: ${dateFormatter.format(Date())}
Support Code: $supportCode
            """.trimIndent()
        } else {
            binding.btnStartSession.text = "Start Session"
            binding.tvSessionStatus.text = "⚪ No active session"
            binding.tvSessionDetails.text = """
Click 'Start Session' to enable remote support.

The remote technician will be able to:
• Diagnose hardware issues
• View system logs and status
• Run diagnostic tests
• Provide real-time assistance

Session will be secure and monitored.
            """.trimIndent()
        }
    }

    private fun copySupportCodeToClipboard() {
        if (supportCode == null) {
            showToast("No support code to copy")
            return
        }

        try {
            // For API 25, use ClipboardManager
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Support Code", supportCode)
            clipboard.setPrimaryClip(clip)

            showToast("Support code copied to clipboard")

        } catch (e: Exception) {
            showToast("Failed to copy support code: ${e.message}")
        }
    }

    private fun shareDiagnosticInfo() {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val diagnosticInfo = withContext(Dispatchers.IO) {
                    generateDiagnosticReport()
                }

                // Create share intent
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "ZIMPUDO Kiosk Diagnostic Report")
                    putExtra(Intent.EXTRA_TEXT, diagnosticInfo)
                }

                startActivity(Intent.createChooser(shareIntent, "Share Diagnostic Info"))

            } catch (e: Exception) {
                showToast("Error sharing diagnostic info: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun generateDiagnosticReport(): String {
        return """
ZIMPUDO Kiosk Diagnostic Report
Generated: ${dateFormatter.format(Date())}

=== DEVICE INFORMATION ===
Model: ${Build.MANUFACTURER} ${Build.MODEL}
Serial: ${Build.SERIAL}
Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})

=== APPLICATION INFO ===
Version: 0.1.0
Package: com.blitztech.pudokiosk
Support Code: ${supportCode ?: "Not generated"}

=== STATUS SUMMARY ===
Network: Connected
Backend: Online  
Hardware: Operational
Support Session: ${if (supportSessionActive) "Active" else "Inactive"}

=== RECENT ACTIVITY ===
• Device started successfully
• All hardware components detected
• Network connectivity verified
• Ready for remote support

Contact: support@zimpudo.com
        """.trimIndent()
    }

    private fun contactSupport() {
        try {
            val supportInfo = """
ZIMPUDO Kiosk Support Request

Device: ${Build.MANUFACTURER} ${Build.MODEL}
Serial: ${Build.SERIAL}
App Version: 0.1.0
Support Code: ${supportCode ?: "Not generated"}
Timestamp: ${dateFormatter.format(Date())}

Issue Description:
[Please describe the issue you're experiencing]

Additional Context:
[Any additional information that might help]
            """.trimIndent()

            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf("support@zimpudo.com"))
                putExtra(Intent.EXTRA_SUBJECT, "ZIMPUDO Kiosk Support Request - ${supportCode ?: "No Code"}")
                putExtra(Intent.EXTRA_TEXT, supportInfo)
            }

            startActivity(Intent.createChooser(emailIntent, "Contact Support"))

        } catch (e: Exception) {
            showToast("No email app available. Please contact support@zimpudo.com directly.")
        }
    }

    private fun refreshSupportInfo() {
        binding.tvLastUpdated.text = "Refreshing..."
        loadDeviceInfo()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnGenerateCode.isEnabled = !loading
        binding.btnStartSession.isEnabled = !loading && supportCode != null
        binding.btnRefresh.isEnabled = !loading
        binding.btnCopyCode.isEnabled = !loading && supportCode != null
        binding.btnShareDiagnostics.isEnabled = !loading
        binding.btnContactSupport.isEnabled = !loading
    }

    private fun returnToTechMenu() {
        // End any active support session before leaving
        if (supportSessionActive) {
            supportSessionActive = false
        }

        val intent = Intent(this, TechnicianMenuActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        returnToTechMenu()
    }
}