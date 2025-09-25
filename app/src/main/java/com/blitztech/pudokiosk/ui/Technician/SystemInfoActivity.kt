package com.blitztech.pudokiosk.ui.Technician

import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.databinding.ActivitySystemInfoBinding
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.*

/**
 * System Info Activity - Display comprehensive system information
 * Compatible with Android API 25 (Android 7.1.2)
 */
class SystemInfoActivity : BaseKioskActivity() {

    private lateinit var binding: ActivitySystemInfoBinding
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private var isLoading = false

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
            if (!isLoading) {
                refreshSystemInfo()
            }
        }

        binding.btnExport.setOnClickListener {
            exportSystemInfo()
        }
    }

    private fun loadSystemInfo() {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val systemInfo = withContext(Dispatchers.IO) {
                    buildSystemInfoString()
                }

                binding.tvSystemInfoContent.text = systemInfo
                binding.tvLastUpdated.text = "Last updated: ${dateFormatter.format(Date())}"

            } catch (e: Exception) {
                binding.tvSystemInfoContent.text = "Error loading system info: ${e.message}"
                showToast("Error: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun buildSystemInfoString(): String {
        val systemInfo = StringBuilder()

        systemInfo.append(getDeviceInfo())
        systemInfo.append("\n\n")
        systemInfo.append(getAndroidInfo())
        systemInfo.append("\n\n")
        systemInfo.append(getAppInfo()) // RENAMED - was getApplicationInfo()
        systemInfo.append("\n\n")
        systemInfo.append(getHardwareInfo())
        systemInfo.append("\n\n")
        systemInfo.append(getMemoryInfo())
        systemInfo.append("\n\n")
        systemInfo.append(getStorageInfo())
        systemInfo.append("\n\n")
        systemInfo.append(getCpuInfo())

        return systemInfo.toString()
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

    // RENAMED METHOD - was getApplicationInfo() which conflicted with Android's built-in method
    private fun getAppInfo(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            """
=== APPLICATION INFO ===
Package Name: ${packageInfo.packageName}
Version Name: ${packageInfo.versionName}
Version Code: ${packageInfo.versionCode}
Target SDK: ${packageInfo.applicationInfo?.targetSdkVersion}
Min SDK: ${packageInfo.applicationInfo?.targetSdkVersion}
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
            val memInfo = ActivityManager.MemoryInfo()
            val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            activityManager.getMemoryInfo(memInfo)

            """
=== MEMORY INFORMATION ===
Available RAM: ${formatBytes(memInfo.availMem)}
Total RAM: ${formatBytes(memInfo.totalMem)}
Low Memory Threshold: ${formatBytes(memInfo.threshold)}
Low Memory: ${memInfo.lowMemory}

App Memory Usage:
- Used: ${formatBytes(usedMemory)}
- Free: ${formatBytes(runtime.freeMemory())}
- Total: ${formatBytes(totalMemory)}
- Max Available: ${formatBytes(maxMemory)}
            """.trimIndent()
        } catch (e: Exception) {
            "=== MEMORY INFORMATION ===\nError retrieving memory info: ${e.message}"
        }
    }

    private fun getStorageInfo(): String {
        return try {
            val internalPath = Environment.getDataDirectory()
            val internalStat = StatFs(internalPath.path)
            val internalBlockSize = internalStat.blockSizeLong
            val internalTotalBlocks = internalStat.blockCountLong
            val internalAvailableBlocks = internalStat.availableBlocksLong

            val internalTotal = internalTotalBlocks * internalBlockSize
            val internalAvailable = internalAvailableBlocks * internalBlockSize
            val internalUsed = internalTotal - internalAvailable

            """
=== STORAGE INFORMATION ===
Internal Storage:
- Total: ${formatBytes(internalTotal)}
- Used: ${formatBytes(internalUsed)}
- Available: ${formatBytes(internalAvailable)}
- Usage: ${String.format("%.1f", (internalUsed * 100.0 / internalTotal))}%

App Data Directory: ${filesDir.absolutePath}
Cache Directory: ${cacheDir.absolutePath}
            """.trimIndent()
        } catch (e: Exception) {
            "=== STORAGE INFORMATION ===\nError retrieving storage info: ${e.message}"
        }
    }

    private fun getCpuInfo(): String {
        return try {
            val cpuInfo = StringBuilder()
            cpuInfo.append("=== CPU INFORMATION ===\n")
            cpuInfo.append("Processor Count: ${Runtime.getRuntime().availableProcessors()}\n")
            cpuInfo.append("Architecture: ${Build.CPU_ABI}\n")
            cpuInfo.append("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}\n\n")

            try {
                val reader = BufferedReader(FileReader("/proc/cpuinfo"))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.startsWith("processor") ||
                        line!!.startsWith("model name") ||
                        line!!.startsWith("cpu MHz") ||
                        line!!.startsWith("Features") ||
                        line!!.startsWith("Hardware")) {
                        cpuInfo.append("$line\n")
                    }
                }
                reader.close()
            } catch (e: Exception) {
                cpuInfo.append("CPU Details: Unable to read /proc/cpuinfo (${e.message})")
            }

            cpuInfo.toString()
        } catch (e: Exception) {
            "=== CPU INFORMATION ===\nError retrieving CPU info: ${e.message}"
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
        loadSystemInfo()
    }

    private fun exportSystemInfo() {
        try {
            val systemInfo = binding.tvSystemInfoContent.text.toString()

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "ZIMPUDO Kiosk System Information")
                putExtra(Intent.EXTRA_TEXT, systemInfo)
            }

            startActivity(Intent.createChooser(shareIntent, "Export System Info"))

        } catch (e: Exception) {
            showToast("Error exporting system info: ${e.message}")
        }
    }

    private fun returnToTechMenu() {
        finish()
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        binding.progressBar?.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRefresh.isEnabled = !loading
        binding.btnExport.isEnabled = !loading && binding.tvSystemInfoContent.text.isNotEmpty()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}