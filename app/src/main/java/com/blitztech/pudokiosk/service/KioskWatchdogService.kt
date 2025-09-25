package com.blitztech.pudokiosk.service

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.blitztech.pudokiosk.ui.main.MainActivity

/**
 * Watchdog service that ensures the kiosk app stays active
 * Monitors running tasks and brings the app to foreground if needed
 */
class KioskWatchdogService : Service() {

    companion object {
        private const val TAG = "KioskWatchdogService"
        private const val WATCHDOG_INTERVAL = 2000L // Check every 2 seconds
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private lateinit var activityManager: ActivityManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "KioskWatchdogService created")
        activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "KioskWatchdogService started")
        startWatchdog()
        return START_STICKY // Restart if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startWatchdog() {
        if (isRunning) return

        isRunning = true
        Log.d(TAG, "Starting kiosk watchdog monitoring")

        val watchdogRunnable = object : Runnable {
            override fun run() {
                if (isRunning) {
                    try {
                        checkKioskApp()
                        handler.postDelayed(this, WATCHDOG_INTERVAL)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in watchdog check", e)
                        handler.postDelayed(this, WATCHDOG_INTERVAL)
                    }
                }
            }
        }

        handler.post(watchdogRunnable)
    }

    private fun checkKioskApp() {
        try {
            // Get list of running tasks
            val runningTasks = activityManager.getRunningTasks(1)

            if (runningTasks.isNotEmpty()) {
                val topActivity = runningTasks[0].topActivity
                val packageName = packageName

                //Log.v(TAG, "Top activity: ${topActivity?.className}, Our package: $packageName")

                // Check if our kiosk app is the top activity
                if (topActivity?.packageName != packageName) {
                    Log.w(TAG, "Kiosk app not in foreground! Top app: ${topActivity?.packageName}")
                    bringKioskToForeground()
                } else if (topActivity.className != MainActivity::class.java.name) {
                    // Our app is running but not on MainActivity (could be normal)
                    //Log.d(TAG, "Kiosk app running, current activity: ${topActivity.className}")
                }
            } else {
                Log.w(TAG, "No running tasks found - bringing kiosk to foreground")
                bringKioskToForeground()
            }

        } catch (e: SecurityException) {
            // GET_TASKS permission might not be available
            Log.w(TAG, "Cannot check running tasks due to security restrictions", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking kiosk app status", e)
        }
    }

    private fun bringKioskToForeground() {
        try {
            Log.d(TAG, "Bringing kiosk app to foreground")

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            startActivity(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to bring kiosk app to foreground", e)
        }
    }

    private fun stopWatchdog() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "Kiosk watchdog stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWatchdog()
        Log.d(TAG, "KioskWatchdogService destroyed")
    }
}