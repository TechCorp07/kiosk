package com.blitztech.pudokiosk.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * SyncScheduler — manages the WorkManager periodic sync job.
 *
 * Call [schedule] once from [ZimpudoApp.onCreate] to keep the outbox
 * continuously drained in the background. Call [enqueueImmediate] after
 * any operation that creates an outbox event while the device might be offline.
 */
object SyncScheduler {

    private const val TAG = "SyncScheduler"

    /** Minimum interval between periodic syncs (WorkManager enforces ≥ 15 min). */
    private const val PERIODIC_INTERVAL_MINUTES = 15L

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Registers a periodic sync job that survives app restarts.
     * Safe to call multiple times — WorkManager deduplicates via [SyncWorker.WORK_NAME].
     */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            PERIODIC_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,   // don't reset timer if already scheduled
            request
        )

        Log.d(TAG, "Periodic sync scheduled (every ${PERIODIC_INTERVAL_MINUTES}m, network required)")
    }

    /**
     * Enqueues a one-time sync right now (when online).
     * Use after writing an event to the outbox during an active session.
     */
    fun enqueueImmediate(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(request)
        Log.d(TAG, "Immediate sync enqueued")
    }
}
