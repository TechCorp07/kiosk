package com.blitztech.pudokiosk.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.blitztech.pudokiosk.R
import kotlinx.coroutines.*

class DeviceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startForeground(1001, buildNotification())
        // schedule periodic outbox sync
        com.blitztech.pudokiosk.data.sync.WorkScheduler.scheduleOutbox(this)
        // Placeholders: later we'll add USB attach/detach monitors, heartbeats, etc.
        com.blitztech.pudokiosk.data.sync.WorkScheduler.scheduleConfig(this)

        val filter = android.content.IntentFilter().apply {
            addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                android.util.Log.i("DeviceService", "USB event: ${i?.action}")
            }
        }, filter)

        scope.launch {
            while (isActive) {
                // TODO: heartbeat, queue flush, device link checks
                delay(60_000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "device_service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.notif_channel_desc) }
            mgr.createNotificationChannel(ch)
        }

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_running))
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        fun start(ctx: Context) {
            val i = Intent(ctx, DeviceService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(i)
            } else ctx.startService(i)
        }
    }
}
