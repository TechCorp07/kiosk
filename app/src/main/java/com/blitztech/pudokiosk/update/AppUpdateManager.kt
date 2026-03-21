package com.blitztech.pudokiosk.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.blitztech.pudokiosk.service.KioskLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles checking for updates, downloading APKs, and triggering installation.
 *
 * If the app is Device Owner → silent install via PackageInstaller (no user prompt).
 * Otherwise → standard ACTION_INSTALL_PACKAGE intent (user must confirm).
 */
object AppUpdateManager {

    private const val TAG = "AppUpdateManager"

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val releaseNotes: String,
        val mandatory: Boolean
    )

    /**
     * Check the remote server for a newer version.
     * Returns UpdateInfo if an update is available, null otherwise.
     */
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for updates at ${UpdateConfig.UPDATE_CHECK_URL}")

            val url = URL(UpdateConfig.UPDATE_CHECK_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = (UpdateConfig.CONNECT_TIMEOUT_SECONDS * 1000).toInt()
            connection.readTimeout = (UpdateConfig.CONNECT_TIMEOUT_SECONDS * 1000).toInt()
            connection.setRequestProperty("Accept", "application/json")

            // Include device info in headers for server-side targeting
            connection.setRequestProperty("X-Device-Id", Build.SERIAL ?: "unknown")
            connection.setRequestProperty("X-App-Version", getAppVersionCode(context).toString())
            connection.setRequestProperty("X-App-Package", context.packageName)

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Update check failed with HTTP $responseCode")
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(response)
            val serverVersionCode = json.getInt("versionCode")
            val currentVersionCode = getAppVersionCode(context)

            Log.d(TAG, "Current version: $currentVersionCode, Server version: $serverVersionCode")

            if (serverVersionCode > currentVersionCode) {
                val info = UpdateInfo(
                    versionCode = serverVersionCode,
                    versionName = json.optString("versionName", "unknown"),
                    apkUrl = json.getString("apkUrl"),
                    releaseNotes = json.optString("releaseNotes", ""),
                    mandatory = json.optBoolean("mandatory", false)
                )
                Log.d(TAG, "Update available: ${info.versionName} (code ${info.versionCode})")
                info
            } else {
                Log.d(TAG, "App is up to date")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            null
        }
    }

    /**
     * Download the APK from the given URL and trigger installation.
     */
    suspend fun downloadAndInstall(context: Context, updateInfo: UpdateInfo): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Downloading APK from ${updateInfo.apkUrl}")

                val apkFile = downloadApk(context, updateInfo.apkUrl)
                if (apkFile == null) {
                    Log.e(TAG, "APK download failed")
                    return@withContext false
                }

                Log.d(TAG, "APK downloaded: ${apkFile.absolutePath} (${apkFile.length()} bytes)")

                // Use the appropriate install method
                withContext(Dispatchers.Main) {
                    if (KioskLockManager.isDeviceOwner(context)) {
                        silentInstall(context, apkFile)
                    } else {
                        promptInstall(context, apkFile)
                    }
                }

                true
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading/installing update", e)
                false
            }
        }

    /**
     * Download APK to the app's cache directory.
     */
    private fun downloadApk(context: Context, apkUrl: String): File? {
        return try {
            val url = URL(apkUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = (UpdateConfig.CONNECT_TIMEOUT_SECONDS * 1000).toInt()
            connection.readTimeout = (UpdateConfig.READ_TIMEOUT_SECONDS * 1000).toInt()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Download failed: HTTP ${connection.responseCode}")
                return null
            }

            val apkFile = File(context.cacheDir, "update.apk")
            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(UpdateConfig.DOWNLOAD_BUFFER_SIZE)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                    }
                    Log.d(TAG, "Downloaded $totalBytesRead bytes")
                }
            }

            connection.disconnect()
            apkFile
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK", e)
            null
        }
    }

    /**
     * Silent install using PackageInstaller (Device Owner only).
     * No user prompt — the app is updated automatically.
     */
    private fun silentInstall(context: Context, apkFile: File) {
        try {
            Log.d(TAG, "Starting silent install (Device Owner)")

            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            // Write the APK to the session
            session.openWrite("update.apk", 0, apkFile.length()).use { output ->
                apkFile.inputStream().use { input ->
                    input.copyTo(output)
                }
                session.fsync(output)
            }

            // Create a status receiver intent
            val intent = Intent(context, UpdateInstallReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            // Commit the session (triggers silent install)
            session.commit(pendingIntent.intentSender)
            Log.d(TAG, "Silent install session committed")
        } catch (e: Exception) {
            Log.e(TAG, "Silent install failed, falling back to prompt install", e)
            promptInstall(context, apkFile)
        }
    }

    /**
     * Standard install prompt using ACTION_VIEW intent.
     * User must confirm the installation.
     */
    private fun promptInstall(context: Context, apkFile: File) {
        try {
            Log.d(TAG, "Starting prompted install")

            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // API 24+: use FileProvider
                val apkUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        Uri.fromFile(apkFile),
                        "application/vnd.android.package-archive"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            context.startActivity(intent)
            Log.d(TAG, "Install prompt launched")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching install prompt", e)
        }
    }

    /**
     * Get the current app version code.
     */
    private fun getAppVersionCode(context: Context): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app version code", e)
            0
        }
    }

    /**
     * Get the current app version name.
     */
    fun getAppVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}
