package com.blitztech.pudokiosk.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.dto.courier.TransactionRequest
import com.blitztech.pudokiosk.data.api.dto.collection.LockerPickupRequest
import com.blitztech.pudokiosk.data.db.AppDatabase
import com.blitztech.pudokiosk.data.db.OutboxEventEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

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

            // Build Moshi once per sync run, reuse across all events
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val deliveredKeys = mutableListOf<String>()
            var failures = 0

            for (event in pending) {
                try {
                    val dispatched = dispatchEvent(event, moshi)
                    if (dispatched) {
                        deliveredKeys.add(event.idempotencyKey)
                        Log.d(TAG, "  ✓ Synced [${event.type}] key=${event.idempotencyKey}")
                    } else {
                        Log.w(TAG, "  ✗ Dispatch returned false for ${event.idempotencyKey}")
                        failures++
                    }
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

    // ── Event dispatcher — routes by type ─────────────────────────────────
    private suspend fun dispatchEvent(event: OutboxEventEntity, moshi: Moshi): Boolean {
        val api = ZimpudoApp.apiRepository
        val prefs = ZimpudoApp.prefs
        val token = prefs.getAccessToken().orEmpty()
        if (token.isBlank()) {
            Log.w(TAG, "No access token — cannot dispatch event ${event.type}")
            return false
        }

        return when (event.type) {
            "courier_dropoff" -> {
                val req = moshi.adapter(TransactionRequest::class.java).fromJson(event.payloadJson)
                    ?: return false
                val result = api.courierDropoff(req, token)
                result is NetworkResult.Success
            }
            "courier_pickup" -> {
                val req = moshi.adapter(TransactionRequest::class.java).fromJson(event.payloadJson)
                    ?: return false
                val result = api.courierPickup(req, token)
                result is NetworkResult.Success
            }
            "collection_confirmed" -> {
                val req = moshi.adapter(LockerPickupRequest::class.java).fromJson(event.payloadJson)
                    ?: return false
                val result = api.completePickup(req, token)
                result is NetworkResult.Success
            }
            "courier_issue_report" -> {
                // TODO: Wire to dedicated backend issue-reporting endpoint when available.
                // For now, keep in outbox (return false) so it retries once the endpoint exists.
                Log.d(TAG, "courier_issue_report event pending backend endpoint — will retry")
                false
            }
            else -> {
                // Unknown type — mark as delivered to avoid blocking the queue
                Log.w(TAG, "Unknown event type '${event.type}' — marking delivered without dispatch")
                true
            }
        }
    }

    // ── Database access ─────────────────────────────────────────────
    private fun getDatabase(): AppDatabase {
        // Use the application-level singleton now that it is exposed.
        return ZimpudoApp.database
    }
}


