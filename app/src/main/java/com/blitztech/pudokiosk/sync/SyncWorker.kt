package com.blitztech.pudokiosk.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.config.ApiEndpoints
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

            // Cleanup: purge delivered entries older than 7 days
            val cutoff = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
            outboxDao.deleteDeliveredBefore(cutoff)
            Log.d(TAG, "🧹 Purged outbox events older than 7 days (cutoff=${java.util.Date(cutoff)})")  

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

            // ── Recipient collection (public endpoint — no Bearer token needed) ──
            "collection_confirmed" -> {
                val req = moshi.adapter(LockerPickupRequest::class.java).fromJson(event.payloadJson)
                    ?: return false
                val result = api.completePickup(req, token)  // Pass token as required by repository
                result is NetworkResult.Success
            }

            // ── Courier pickup scan (barcode → orders-service) ──────────────────
            "courier_pickup_scan" -> {
                val map = moshi.adapter(Map::class.java).fromJson(event.payloadJson)
                    ?: return false
                @Suppress("UNCHECKED_CAST")
                val orderId = (map as Map<String, Any?>)["orderId"] as? String ?: return false
                val barcode = map["barcode"] as? String ?: return false
                val result = api.courierPickupScan(orderId, barcode, token)
                result is NetworkResult.Success
            }

            // ── Courier dropoff at locker (orders-service) ──────────────────────
            "courier_dropoff" -> {
                val map = moshi.adapter(Map::class.java).fromJson(event.payloadJson)
                    ?: return false
                @Suppress("UNCHECKED_CAST")
                val m = map as Map<String, Any?>
                val dropoffUrl = m["dropoffUrl"] as? String
                    ?: ApiEndpoints.getCourierDropoffUrl(m["orderId"] as? String ?: return false)
                val barcode = m["barcode"] as? String ?: return false
                val lockerId = m["lockerId"] as? String ?: return false
                val result = api.courierDropoffAtLocker(dropoffUrl, barcode, lockerId, token)
                result is NetworkResult.Success
            }

            // ── Issue report (pending backend endpoint) ─────────────────────────
            "courier_issue_report" -> {
                // TODO: Wire to dedicated backend issue-reporting endpoint when available.
                Log.d(TAG, "courier_issue_report event pending backend endpoint — will retry")
                false
            }

            // ── Security Photo Upload (pending backend endpoint) ────────────────
            "security_photo_upload" -> {
                // TODO [POST-LAUNCH BACKEND]: Upload captured security photos
                // Endpoint POST /api/v1/kiosks/security-photos
                // Action: Fetch photo from device storage using event.payloadJson info,
                //         upload via multipart/form-data, mark event delivered on 200 OK.
                Log.d(TAG, "security_photo_upload event pending backend endpoint — will retry")
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
