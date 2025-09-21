package com.blitztech.pudokiosk.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.widget.Toast

object LockTaskController {
    fun startKiosk(activity: Activity) {
        try {
            activity.startLockTask()
            Toast.makeText(activity, "Kiosk mode ON", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(activity, "Failed to start kiosk (needs device owner)", Toast.LENGTH_LONG).show()
        }
    }
    fun stopKiosk(activity: Activity) {
        try { activity.stopLockTask() } catch (_: Exception) { }
        Toast.makeText(activity, "Kiosk mode OFF", Toast.LENGTH_SHORT).show()
    }
}
