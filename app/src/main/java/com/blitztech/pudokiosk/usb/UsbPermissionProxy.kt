package com.blitztech.pudokiosk.usb
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UsbPermissionProxy : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // actual handling is done by UsbHelper’s registered receiver.
    }
}
