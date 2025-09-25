package com.blitztech.pudokiosk.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.blitztech.pudokiosk.service.KioskModeService
import com.blitztech.pudokiosk.service.KioskWatchdogService

/**
 * Device Admin Receiver for enhanced kiosk control
 * Provides additional system-level control when app is set as device administrator
 */
class KioskDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "KioskDeviceAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Device Admin enabled for ZIMPUDO Kiosk")

        // Start enhanced kiosk services
        startKioskServices(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "Device Admin disabled for ZIMPUDO Kiosk")
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
        Log.d(TAG, "Device password changed")
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.d(TAG, "Device password failed")
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.d(TAG, "Device password succeeded")

        // Ensure kiosk mode is active after unlock
        startKioskServices(context)
    }

    private fun startKioskServices(context: Context) {
        try {
            // Start kiosk mode service
            context.startService(Intent(context, KioskModeService::class.java))

            // Start watchdog service
            context.startService(Intent(context, KioskWatchdogService::class.java))

            Log.d(TAG, "Kiosk services started from device admin")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start kiosk services", e)
        }
    }
}