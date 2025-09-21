package com.blitztech.pudokiosk.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.blitztech.pudokiosk.data.ServiceLocator

class ConfigSyncWorker(ctx: Context, params: WorkerParameters): CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        val changed = ServiceLocator.config.refresh(deviceId = "locker-${android.os.Build.SERIAL ?: "unknown"}")
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
