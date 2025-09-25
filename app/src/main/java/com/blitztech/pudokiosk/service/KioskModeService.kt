package com.blitztech.pudokiosk.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.ui.main.MainActivity

/**
 * Kiosk Mode Service to keep the app running and handle system events
 */
class KioskModeService : Service() {

    companion object {
        private const val TAG = "KioskModeService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "kiosk_service"
        private const val CHANNEL_NAME = "Kiosk Service"

        @RequiresApi(Build.VERSION_CODES.O)
        fun start(context: Context) {
            val intent = Intent(context, KioskModeService::class.java)
            context.startForegroundService(intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KioskModeService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "KioskModeService started")
        startForeground(NOTIFICATION_ID, createNotification())

        // Ensure main activity is running
        ensureMainActivityRunning()

        return START_STICKY // Restart service if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps ZIMPUDO Kiosk running in background"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ZIMPUDO Kiosk")
            .setContentText("Kiosk mode active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureMainActivityRunning() {
        try {
            val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val tasks = activityManager.getRunningTasks(1)

            if (tasks.isEmpty() || !tasks[0].topActivity?.className.equals(MainActivity::class.java.name)) {
                Log.d(TAG, "Main activity not running, starting it...")
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking/starting main activity", e)
        }
    }
}