package com.blitztech.pudokiosk.usb

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat

class UsbHelper(private val ctx: Context) {
    companion object { const val ACTION_USB_PERMISSION = "com.blitztech.pudokiosk.USB_PERMISSION" }

    private val usb: UsbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
    private var permissionCallback: ((UsbDevice?, Boolean) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_USB_PERMISSION) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                permissionCallback?.invoke(device, granted)
                permissionCallback = null
            }
        }
    }

    fun register() {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        // Use ContextCompat to satisfy Android 13+ requirement at compile-time; safe on API 25.
        ContextCompat.registerReceiver(
            ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED
        )
    }

    fun unregister() {
        try { ctx.unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    fun devices(): Collection<UsbDevice> = usb.deviceList.values
    fun hasPermission(device: UsbDevice) = usb.hasPermission(device)
    fun manager(): UsbManager = usb

    fun requestPermission(device: UsbDevice, onResult: (UsbDevice?, Boolean) -> Unit) {
        permissionCallback = onResult
        // Make the broadcast explicit + set mutability explicitly for targetSdk 31+
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(ctx.packageName)
        val flags = if (android.os.Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val pi = PendingIntent.getBroadcast(ctx, 0, intent, flags)
        usb.requestPermission(device, pi)
    }
}
