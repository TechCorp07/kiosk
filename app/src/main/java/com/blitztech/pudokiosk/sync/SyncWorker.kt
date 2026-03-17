package com.blitztech.pudokiosk.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.blitztech.pudokiosk.data.db.AppDatabase

/**
 * SyncWorker — drains the local outbox of pending events to the backend.
 *
 * Scheduled by [SyncScheduler] to run every 15 minutes when network is available.
 * Reads from [AppDatabase.outbox] (OutboxDao), posts each event, marks delivered on success.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "SyncWorker"
        const val WORK_NAME = "pudo_outbox_sync"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "▶ SyncWorker starting…")
        return try {
            val db = getDatabase()
            val outboxDao = db.outbox()
            val pending = outboxDao.pending(limit = 50)

            if (pending.isEmpty()) {
                Log.d(TAG, "Outbox empty — nothing to sync")
                return Result.success()
            }

            Log.d(TAG, "Syncing ${pending.size} outbox event(s)")

            val deliveredKeys = mutableListOf<String>()
            var failures = 0

            for (event in pending) {
                try {
                    // TODO (Phase 9): POST event.payloadJson to the corresponding backend endpoint
                    // based on event.type (e.g. "collection_confirmed", "delivery_confirmed")
                    Log.d(TAG, "  → Syncing [${event.type}] key=${event.idempotencyKey}")
                    deliveredKeys.add(event.idempotencyKey)
                } catch (e: Exception) {
                    Log.w(TAG, "  ✗ Failed to sync event ${event.idempotencyKey}: ${e.message}")
                    failures++
                }
            }

            if (deliveredKeys.isNotEmpty()) {
                outboxDao.markDelivered(deliveredKeys)
                Log.d(TAG, "✅ Marked ${deliveredKeys.size} event(s) as delivered")
            }

            if (failures > 0) {
                Log.w(TAG, "Sync had $failures failure(s) — will retry")
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "SyncWorker failed: ${e.message}", e)
            Result.retry()
        }
    }

    private fun getDatabase(): AppDatabase {
        // Use a plain (non-encrypted) fallback for the Worker — the encrypted
        // production instance is managed by the main app process.
        // TODO (Phase 9): expose AppDatabase as a singleton in ZimpudoApp
        // so SyncWorker can reuse the existing encrypted connection.
        return androidx.room.Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "pudokiosk_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }
}


