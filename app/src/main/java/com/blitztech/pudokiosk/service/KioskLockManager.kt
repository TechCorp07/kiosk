package com.blitztech.pudokiosk.service

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.receiver.KioskDeviceAdminReceiver

/**
 * Manages Android Lock Task Mode for ATM-like kiosk lockdown.
 *
 * When the app is provisioned as Device Owner, Lock Task Mode disables
 * Home, Recents, notifications, and the status bar at the OS level.
 * Falls back to the existing watchdog-based kiosk mode otherwise.
 */
object KioskLockManager {

    private const val TAG = "KioskLockManager"

    private fun getDevicePolicyManager(context: Context): DevicePolicyManager {
        return context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private fun getAdminComponent(context: Context): ComponentName {
        return ComponentName(context, KioskDeviceAdminReceiver::class.java)
    }

    /**
     * Check if this app is set as Device Owner via:
     * `adb shell dpm set-device-owner com.blitztech.pudokiosk/.receiver.KioskDeviceAdminReceiver`
     */
    fun isDeviceOwner(context: Context): Boolean {
        return try {
            val dpm = getDevicePolicyManager(context)
            dpm.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device owner status", e)
            false
        }
    }

    /**
     * Enable Lock Task Mode — pins the app so the user cannot leave.
     * Must be called from an Activity.
     */
    fun enableLockTaskMode(activity: Activity) {
        // Don't engage lock if in maintenance mode
        if (isMaintenanceMode()) {
            Log.d(TAG, "Maintenance mode active — skipping lock task")
            return
        }

        if (!isDeviceOwner(activity)) {
            Log.d(TAG, "Not Device Owner — using fallback kiosk mode")
            return
        }

        try {
            val dpm = getDevicePolicyManager(activity)
            val admin = getAdminComponent(activity)

            // Whitelist this package for lock task mode
            dpm.setLockTaskPackages(admin, arrayOf(activity.packageName))

            // Configure lock task features (API 28+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Allow nothing: no Home, no Recents, no notifications, no status bar
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }

            // Start lock task
            if (!activity.isInLockTaskMode()) {
                activity.startLockTask()
                Log.d(TAG, "Lock Task Mode ENABLED — device is now locked to this app")
            } else {
                Log.d(TAG, "Lock Task Mode already active")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable Lock Task Mode", e)
        }
    }

    /**
     * Disable Lock Task Mode — allows the technician to leave the app.
     * Called when entering maintenance mode.
     */
    fun disableLockTaskMode(activity: Activity) {
        try {
            if (activity.isInLockTaskMode()) {
                activity.stopLockTask()
                Log.d(TAG, "Lock Task Mode DISABLED — device is unlocked for maintenance")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable Lock Task Mode", e)
        }
    }

    /**
     * Check if lock task mode is currently active on the activity.
     */
    fun isLockTaskActive(activity: Activity): Boolean {
        return try {
            activity.isInLockTaskMode()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if maintenance mode is active from shared preferences.
     */
    fun isMaintenanceMode(): Boolean {
        return try {
            ZimpudoApp.prefs.isMaintenanceMode()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Toggle maintenance mode on/off.
     */
    fun setMaintenanceMode(activity: Activity, enabled: Boolean) {
        try {
            ZimpudoApp.prefs.setMaintenanceMode(enabled)

            if (enabled) {
                disableLockTaskMode(activity)
                Log.d(TAG, "Maintenance mode ON — kiosk unlocked")
            } else {
                enableLockTaskMode(activity)
                Log.d(TAG, "Maintenance mode OFF — kiosk re-locked")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle maintenance mode", e)
        }
    }

    /**
     * Called during app startup to ensure lock task is engaged if appropriate.
     */
    fun ensureLockTaskOnStartup(activity: Activity) {
        if (isMaintenanceMode()) {
            Log.d(TAG, "Startup: maintenance mode is active, not locking")
            return
        }
        enableLockTaskMode(activity)
    }

    /**
     * Check if the Activity.isInLockTaskMode() API is available (API 23+).
     */
    private fun Activity.isInLockTaskMode(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.lockTaskModeState != android.app.ActivityManager.LOCK_TASK_MODE_NONE
        } else {
            false
        }
    }
}
