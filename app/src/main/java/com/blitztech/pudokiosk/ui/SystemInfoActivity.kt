package com.blitztech.pudokiosk.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.blitztech.pudokiosk.databinding.ActivitySystemInfoBinding
import java.io.BufferedReader
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.*

/**
 * System Info Activity - Display comprehensive system information
 * Compatible with Android API 25 (Android 7.1.2)
 */
class SystemInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySystemInfoBinding
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySystemInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        setupClickListeners()
        loadSystemInfo()
    }

    private fun setupViews() {
        binding.tvTitle.text = "System Information"
        binding.tvSubtitle.text = "Hardware and software specifications"
        binding.tvLastUpdated.text = "Last updated: ${dateFormatter.format(Date())}"
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            returnToTechMenu()
        }

        binding.btnRefresh.setOnClickListener {
            refreshSystemInfo()
        }

        binding.btnExport.setOnClickListener {
            exportSystemInfo()
        }
    }

    private fun loadSystemInfo() {
        setLoading(true)

        try {
            val systemInfo = StringBuilder()

            systemInfo.append(getDeviceInfo())
            systemInfo.append("\n")
            systemInfo.append(getAndroidInfo())
            systemInfo.append("\n")
            systemInfo.append(getApplicationInfo())
            systemInfo.append("\n")
            systemInfo.append(getHardwareInfo())
            systemInfo.append("\n")
            systemInfo.append(getMemoryInfo())
            systemInfo.append("\n")
            systemInfo.append(getStorageInfo())
            systemInfo.append("\n")
            systemInfo.append(getCpuInfo())

            binding.tvSystemInfoContent.text = systemInfo.toString()

        } catch (e: Exception) {
            binding.tvSystemInfoContent.text = "Error loading system info: ${e.message}"
            showToast("Error: ${e.message}")
        } finally {
            setLoading(false)
        }
    }

    private fun getDeviceInfo(): String {
        return """
=== DEVICE INFORMATION ===
Manufacturer: ${Build.MANUFACTURER}
Brand: ${Build.BRAND}
Model: ${Build.MODEL}
Device: ${Build.DEVICE}
Product: ${Build.PRODUCT}
Hardware: ${Build.HARDWARE}
Board: ${Build.BOARD}
Serial: ${Build.SERIAL}
        """.trimIndent()
    }

    private fun getAndroidInfo(): String {
        return """
=== ANDROID SYSTEM ===
Version: ${Build.VERSION.RELEASE}
SDK Level: ${Build.VERSION.SDK_INT}
Codename: ${Build.VERSION.CODENAME}
Build ID: ${Build.ID}
Build Type: ${Build.TYPE}
Tags: ${Build.TAGS}
Fingerprint: ${Build.FINGERPRINT}
        """.trimIndent()
    }

    private fun getApplicationInfo(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            """
=== APPLICATION INFO ===
Package Name: ${packageInfo.packageName}
Version Name: ${packageInfo.versionName}
Version Code: ${packageInfo.versionCode}
Target SDK: ${packageInfo.applicationInfo.targetSdkVersion}
Min SDK: ${packageInfo.applicationInfo.targetSdkVersion}
Install Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(packageInfo.firstInstallTime))}
Update Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(packageInfo.lastUpdateTime))}
            """.trimIndent()
        } catch (e: Exception) {
            "=== APPLICATION INFO ===\nError retrieving app info: ${e.message}"
        }
    }

    private fun getHardwareInfo(): String {
        return """
=== HARDWARE SPECS ===
Screen Density: ${resources.displayMetrics.density} (${resources.displayMetrics.densityDpi} DPI)
Screen Size: ${resources.displayMetrics.widthPixels} x ${resources.displayMetrics.heightPixels}
Processor Count: ${Runtime.getRuntime().availableProcessors()}
Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}
Architecture: ${Build.CPU_ABI}
        """.trimIndent()
    }

    private fun getMemoryInfo(): String {
        return try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val totalMemory = runtime.totalMemory()

            // Try to get system memory info
            val memInfo = android.app.ActivityManager.MemoryInfo()
            val activityManager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            activityManager.getMemoryInfo(memInfo)

            """
=== MEMORY INFORMATION ===
App Memory Usage: ${formatBytes(usedMemory)}
App Memory Available: ${formatBytes(maxMemory)}
App Memory Total: ${formatBytes(totalMemory)}
System Available: ${formatBytes(memInfo.availMem)}
System Total: ${formatBytes(memInfo.totalMem)}
Low Memory: ${memInfo.lowMemory}
Memory Threshold: ${formatBytes(memInfo.threshold)}
            """.trimIndent()
        } catch (e: Exception) {
            "=== MEMORY INFORMATION ===\nError retrieving memory info: ${e.message}"
        }
    }

    private fun getStorageInfo(): String {
        return try {
            val internalPath = Environment.getDataDirectory()
            val internalStat = StatFs(internalPath.path)
            val internalTotal = internalStat.blockCountLong * internalStat.blockSizeLong
            val internalAvailable = internalStat.availableBlocksLong * internalStat.blockSizeLong
            val internalUsed = internalTotal - internalAvailable

            val externalPath = Environment.getExternalStorageDirectory()
            val externalStat = StatFs(externalPath.path)
            val externalTotal = externalStat.blockCountLong * externalStat.blockSizeLong
            val externalAvailable = externalStat.availableBlocksLong * externalStat.blockSizeLong
            val externalUsed = externalTotal - externalAvailable

            """
=== STORAGE INFORMATION ===
Internal Storage:
  Total: ${formatBytes(internalTotal)}
  Used: ${formatBytes(internalUsed)}
  Available: ${formatBytes(internalAvailable)}
  
External Storage:
  Total: ${formatBytes(externalTotal)}
  Used: ${formatBytes(externalUsed)}
  Available: ${formatBytes(externalAvailable)}
  State: ${Environment.getExternalStorageState()}
            """.trimIndent()
        } catch (e: Exception) {
            "=== STORAGE INFORMATION ===\nError retrieving storage info: ${e.message}"
        }
    }

    private fun getCpuInfo(): String {
        return try {
            val cpuInfo = StringBuilder()
            cpuInfo.append("=== CPU INFORMATION ===\n")

            // Try to read CPU info from /proc/cpuinfo
            val reader = BufferedReader(FileReader("/proc/cpuinfo"))
            reader.useLines { lines ->
                lines.take(20).forEach { line ->
                    if (line.contains("processor") ||
                        line.contains("model name") ||
                        line.contains("cpu MHz") ||
                        line.contains("cache size") ||
                        line.contains("bogomips") ||
                        line.contains("Hardware") ||
                        line.contains("Revision")) {
                        cpuInfo.append("$line\n")
                    }
                }
            }

            if (cpuInfo.length <= 30) { // Only header was added
                cpuInfo.append("CPU info not available from /proc/cpuinfo\n")
                cpuInfo.append("Cores: ${Runtime.getRuntime().availableProcessors()}\n")
                cpuInfo.append("Architecture: ${Build.CPU_ABI}\n")
            }

            cpuInfo.toString()
        } catch (e: Exception) {
            """
=== CPU INFORMATION ===
Error reading CPU info: ${e.message}
Available Processors: ${Runtime.getRuntime().availableProcessors()}
CPU Architecture: ${Build.CPU_ABI}
            """.trimIndent()
        }
    }

    private fun formatBytes(bytes: Long): String {
        val unit = 1024
        if (bytes < unit) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
    }

    private fun refreshSystemInfo() {
        binding.tvLastUpdated.text = "Refreshing..."
        loadSystemInfo()
        binding.tvLastUpdated.text = "Last updated: ${dateFormatter.format(Date())}"
    }

    private fun exportSystemInfo() {
        val systemInfo = binding.tvSystemInfoContent.text.toString()

        if (systemInfo.isEmpty()) {
            showToast("No system info to export")
            return
        }

        try {
            // For API 25 compatibility, show export options
            showToast("Export: System info ready (${systemInfo.length} characters)")

            // In a real implementation, you would:
            // 1. Save to external storage
            // 2. Share via intent
            // 3. Copy to clipboard

        } catch (e: Exception) {
            showToast("Export failed: ${e.message}")
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnRefresh.isEnabled = !loading
        binding.btnExport.isEnabled = !loading
    }

    private fun returnToTechMenu() {
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