package com.blitztech.pudokiosk.ui.technician

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.databinding.ActivityDataManagementBinding
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data Management Activity - Clear cache, logs, and user data
 * Compatible with Android API 25 (Android 7.1.2)
 */
class DataManagementActivity : BaseKioskActivity() {

    private lateinit var binding: ActivityDataManagementBinding
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        setupClickListeners()
        refreshStorageInfo()
    }

    private fun setupViews() {
        binding.tvTitle.text = "Data Management"
        binding.tvSubtitle.text = "Clear cache, logs, and manage app data"
        binding.tvLastUpdated.text = "Last updated: ${dateFormatter.format(Date())}"
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            returnToTechMenu()
        }

        binding.btnRefresh.setOnClickListener {
            refreshStorageInfo()
        }

        // Cache Management
        binding.btnClearCache.setOnClickListener {
            showConfirmationDialog(
                "Clear Cache",
                "This will clear temporary files and cached data. Continue?",
                { clearCache() }
            )
        }

        // Log Management
        binding.btnClearLogs.setOnClickListener {
            showConfirmationDialog(
                "Clear Logs",
                "This will clear all application logs. Continue?",
                { clearLogs() }
            )
        }

        // User Data
        binding.btnClearUserData.setOnClickListener {
            showConfirmationDialog(
                "Clear User Data",
                "⚠️ WARNING: This will remove all user settings and login data. Continue?",
                { clearUserData() }
            )
        }

        // Database Reset
        binding.btnResetDatabase.setOnClickListener {
            showConfirmationDialog(
                "Reset Database",
                "⚠️ CRITICAL: This will delete ALL stored data including packages and user accounts. This cannot be undone! Continue?",
                { resetDatabase() }
            )
        }

        // Export Data
        binding.btnExportData.setOnClickListener {
            exportUserData()
        }

        // Factory Reset
        binding.btnFactoryReset.setOnClickListener {
            showConfirmationDialog(
                "Factory Reset",
                "🚨 DANGER: This will reset the app to factory defaults. ALL DATA WILL BE LOST! Continue?",
                { performFactoryReset() }
            )
        }
    }

    private fun refreshStorageInfo() {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val storageInfo = withContext(Dispatchers.IO) {
                    getStorageInformation()
                }
                binding.tvStorageInfo.text = storageInfo
                binding.tvLastUpdated.text = "Last updated: ${dateFormatter.format(Date())}"
            } catch (e: Exception) {
                binding.tvStorageInfo.text = "Error loading storage info: ${e.message}"
                showToast("Error: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private suspend fun getStorageInformation(): String = withContext(Dispatchers.IO) {
        val info = StringBuilder()

        try {
            // App data directory
            val dataDir = applicationContext.dataDir
            val dataSize = getDirSize(dataDir)
            info.append("App Data: ${formatBytes(dataSize)}\n")

            // Cache directory
            val cacheDir = applicationContext.cacheDir
            val cacheSize = getDirSize(cacheDir)
            info.append("Cache: ${formatBytes(cacheSize)}\n")

            // External cache
            val externalCacheDir = applicationContext.externalCacheDir
            if (externalCacheDir != null) {
                val externalCacheSize = getDirSize(externalCacheDir)
                info.append("External Cache: ${formatBytes(externalCacheSize)}\n")
            }

            // Database files
            val databasePath = applicationContext.getDatabasePath("kiosk_database")
            if (databasePath.exists()) {
                info.append("Database: ${formatBytes(databasePath.length())}\n")
            }

            // Shared preferences
            val prefsDir = File(applicationContext.dataDir, "shared_prefs")
            if (prefsDir.exists()) {
                val prefsSize = getDirSize(prefsDir)
                info.append("Settings: ${formatBytes(prefsSize)}\n")
            }

            // Log files (if any)
            val logsDir = File(applicationContext.filesDir, "logs")
            if (logsDir.exists()) {
                val logsSize = getDirSize(logsDir)
                info.append("Logs: ${formatBytes(logsSize)}\n")
            }

            val totalSize = dataSize + cacheSize + (externalCacheDir?.let { getDirSize(it) } ?: 0)
            info.append("\nTotal App Storage: ${formatBytes(totalSize)}")

        } catch (e: Exception) {
            info.append("Error calculating storage: ${e.message}")
        }

        info.toString()
    }

    private fun getDirSize(dir: File): Long {
        var size = 0L
        try {
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    size += if (file.isDirectory) {
                        getDirSize(file)
                    } else {
                        file.length()
                    }
                }
            } else {
                size = dir.length()
            }
        } catch (e: Exception) {
            // Ignore permission errors
        }
        return size
    }

    private fun clearCache() {
        setLoading(true)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Clear internal cache
                    deleteDir(cacheDir)

                    // Clear external cache
                    externalCacheDir?.let { deleteDir(it) }
                }

                showToast("Cache cleared successfully")
                refreshStorageInfo()

            } catch (e: Exception) {
                showToast("Error clearing cache: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun clearLogs() {
        setLoading(true)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Clear application logs
                    val logsDir = File(filesDir, "logs")
                    if (logsDir.exists()) {
                        deleteDir(logsDir)
                    }

                    // Clear any other log files
                    Runtime.getRuntime().exec("logcat -c")
                }

                showToast("Logs cleared successfully")
                refreshStorageInfo()

            } catch (e: Exception) {
                showToast("Error clearing logs: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun clearUserData() {
        setLoading(true)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Clear shared preferences
                    val prefsDir = File(dataDir, "shared_prefs")
                    if (prefsDir.exists()) {
                        deleteDir(prefsDir)
                    }

                    // Clear any user-specific files
                    val userDataDir = File(filesDir, "user_data")
                    if (userDataDir.exists()) {
                        deleteDir(userDataDir)
                    }
                }

                showToast("User data cleared successfully")
                refreshStorageInfo()

            } catch (e: Exception) {
                showToast("Error clearing user data: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun resetDatabase() {
        setLoading(true)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Delete database files
                    val dbNames = listOf("kiosk_database", "room_database")

                    dbNames.forEach { dbName ->
                        val dbFile = getDatabasePath(dbName)
                        if (dbFile.exists()) {
                            dbFile.delete()
                        }

                        // Delete WAL and SHM files
                        File("${dbFile.path}-wal").delete()
                        File("${dbFile.path}-shm").delete()
                    }
                }

                showToast("Database reset successfully")
                refreshStorageInfo()

            } catch (e: Exception) {
                showToast("Error resetting database: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun exportUserData() {
        setLoading(true)

        lifecycleScope.launch {
            try {
                val exportInfo = withContext(Dispatchers.IO) {
                    // Generate export summary
                    """
Export Summary - ${dateFormatter.format(Date())}

App Version: 0.1.0
Export Type: User Data Backup

Available for Export:
- User settings and preferences
- Application configuration
- Hardware test results
- System logs (if any)
- Database backup

Note: For full export functionality, implement file saving to external storage.
                    """.trimIndent()
                }

                binding.tvStorageInfo.text = exportInfo
                showToast("Export information generated. Implement full export as needed.")

            } catch (e: Exception) {
                showToast("Export failed: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun performFactoryReset() {
        setLoading(true)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Clear everything
                    deleteDir(cacheDir)
                    externalCacheDir?.let { deleteDir(it) }

                    // Clear preferences
                    val prefsDir = File(dataDir, "shared_prefs")
                    if (prefsDir.exists()) {
                        deleteDir(prefsDir)
                    }

                    // Clear databases
                    val dbNames = listOf("kiosk_database", "room_database")
                    dbNames.forEach { dbName ->
                        val dbFile = getDatabasePath(dbName)
                        if (dbFile.exists()) {
                            dbFile.delete()
                        }
                        File("${dbFile.path}-wal").delete()
                        File("${dbFile.path}-shm").delete()
                    }

                    // Clear files directory
                    deleteDir(filesDir)
                }

                showToast("Factory reset completed. App will restart.")

                // Restart app
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
                finishAffinity()

            } catch (e: Exception) {
                showToast("Factory reset failed: ${e.message}")
                setLoading(false)
            }
        }
    }

    private fun deleteDir(dir: File): Boolean {
        return try {
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { child ->
                    deleteDir(child)
                }
            }
            dir.delete()
        } catch (e: Exception) {
            false
        }
    }

    private fun formatBytes(bytes: Long): String {
        val unit = 1024
        if (bytes < unit) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
        val pre = "KMGTPE"[exp - 1]
        return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
    }

    private fun showConfirmationDialog(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Confirm") { _, _ -> action() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRefresh.isEnabled = !loading
        binding.btnClearCache.isEnabled = !loading
        binding.btnClearLogs.isEnabled = !loading
        binding.btnClearUserData.isEnabled = !loading
        binding.btnResetDatabase.isEnabled = !loading
        binding.btnExportData.isEnabled = !loading
        binding.btnFactoryReset.isEnabled = !loading
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