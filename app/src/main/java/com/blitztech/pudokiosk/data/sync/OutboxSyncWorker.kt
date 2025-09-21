package com.blitztech.pudokiosk.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.blitztech.pudokiosk.data.ServiceLocator

class OutboxSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            val ok = ServiceLocator.outbox.flushOnce()
            if (ok) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
