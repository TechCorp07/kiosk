package com.blitztech.pudokiosk.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val OUTBOX_WORK = "outbox_periodic"
    private const val CONFIG_WORK = "config_periodic"

    fun scheduleOutbox(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = PeriodicWorkRequestBuilder<OutboxSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            OUTBOX_WORK, ExistingPeriodicWorkPolicy.UPDATE, req
        )
    }

    fun flushOutboxNow(context: Context) {
        val req = androidx.work.OneTimeWorkRequestBuilder<OutboxSyncWorker>().build()
        androidx.work.WorkManager.getInstance(context).enqueue(req)
    }

    fun scheduleConfig(context: Context) {
        val req = androidx.work.PeriodicWorkRequestBuilder<com.blitztech.pudokiosk.data.sync.ConfigSyncWorker>(
            30, java.util.concurrent.TimeUnit.MINUTES
        ).build()
        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CONFIG_WORK,
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }
}
