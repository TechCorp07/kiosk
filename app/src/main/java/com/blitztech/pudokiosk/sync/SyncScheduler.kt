package com.blitztech.pudokiosk.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * SyncScheduler — manages WorkManager periodic jobs.
 *
 * Jobs managed:
 *   - [SyncWorker]        — drains outbox events every 15 min (network required)
 *   - [LockerSyncWorker]  — syncs cell inventory + sends heartbeat every 15 min (network required)
 *
 * Call [schedule] and [scheduleLockerSync] once from [ZimpudoApp.onCreate].
 */
object SyncScheduler {

    private const val TAG = "SyncScheduler"
    private const val PERIODIC_INTERVAL_MINUTES = 15L

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    // ── Outbox sync ──────────────────────────────────────────────────────

    /**
     * Registers a periodic outbox-drain job.
     * Safe to call multiple times — deduplicates via [SyncWorker.WORK_NAME].
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
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.d(TAG, "Outbox sync scheduled (every ${PERIODIC_INTERVAL_MINUTES}m)")
    }

    /**
     * Enqueues a one-time outbox sync immediately (when back online after offline session).
     */
    fun enqueueImmediate(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueue(request)
        Log.d(TAG, "Immediate outbox sync enqueued")
    }

    // ── Locker sync (cells + heartbeat) ─────────────────────────────────

    /**
     * Registers a periodic locker-sync job for cell inventory and heartbeat.
     * Safe to call multiple times — deduplicates via [LockerSyncWorker.WORK_NAME].
     */
    fun scheduleLockerSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<LockerSyncWorker>(
            PERIODIC_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            LockerSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.d(TAG, "Locker sync scheduled (every ${PERIODIC_INTERVAL_MINUTES}m)")
    }

    /**
     * Runs a one-time locker sync immediately (e.g. on app start or after provisioning).
     */
    fun enqueueLockerSyncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<LockerSyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueue(request)
        Log.d(TAG, "Immediate locker sync enqueued")
    }
}
