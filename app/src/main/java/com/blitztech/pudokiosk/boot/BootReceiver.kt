package com.blitztech.pudokiosk.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.blitztech.pudokiosk.service.DeviceService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            DeviceService.start(context)
        }
    }
}
