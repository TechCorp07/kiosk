
/**
 * Boot receiver to start the kiosk service and main activity on device boot
 */
package com.blitztech.pudokiosk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.blitztech.pudokiosk.service.KioskModeService
import com.blitztech.pudokiosk.ui.main.MainActivity

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Boot completed, action: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "Starting kiosk mode after boot")

                // Start the kiosk service
                KioskModeService.start(context)

                // Start the main activity
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                context.startActivity(mainIntent)
            }
        }
    }
}