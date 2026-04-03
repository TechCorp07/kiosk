package com.blitztech.pudokiosk.offline

import android.util.Log
import com.blitztech.pudokiosk.data.db.AppDatabase
import com.blitztech.pudokiosk.data.db.OfflineCollectionEntity

/**
 * Manages offline OTP validation for recipient collection.
 *
 * When the kiosk has no network, recipients can still collect their parcels
 * if the kiosk has previously synced the OTP data via LockerSyncWorker.
 *
 * Phase 4 implementation: bcrypt-based offline OTP validation using
 * pre-synced hashes from the backend.
 *
 * TODO [P0 BACKEND]: Requires backend to expose:
 *   GET /api/v1/orders/pending-collections?lockerId=...
 *   Returns: [{ trackingNumber, hashedOtp, cellNumber, ... }]
 *
 * For MVP: The kiosk validates the OTP against locally stored bcrypt hashes.
 * The bcrypt library (org.mindrot:jbcrypt:0.4) must be in the build.gradle.
 */
class OfflineCollectionManager(private val db: AppDatabase) {

    companion object {
        private const val TAG = "OfflineCollectionMgr"
    }

    /**
     * Validates a collection attempt offline.
     * Returns the [OfflineCollectionEntity] if the OTP matches, null otherwise.
     *
     * Note: BCrypt check may take 100-500ms — always call from a coroutine.
     */
    suspend fun validateOffline(trackingNumber: String, enteredOtp: String): OfflineCollectionEntity? {
        return try {
            val entry = db.offlineCollections().getByTracking(trackingNumber)
                ?: run {
                    Log.w(TAG, "No offline entry for tracking: $trackingNumber")
                    return null
                }

            if (entry.collected) {
                Log.w(TAG, "Order $trackingNumber already collected offline")
                return null
            }

            // BCrypt check — requires org.mindrot:jbcrypt:0.4 in build.gradle
            val matches = try {
                org.mindrot.jbcrypt.BCrypt.checkpw(enteredOtp, entry.hashedOtp)
            } catch (e: Exception) {
                Log.e(TAG, "BCrypt check failed for $trackingNumber", e)
                false
            }

            if (matches) {
                Log.d(TAG, "Offline OTP validated for $trackingNumber → cell ${entry.cellNumber}")
                entry
            } else {
                Log.w(TAG, "Offline OTP mismatch for $trackingNumber")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Offline validation error for $trackingNumber", e)
            null
        }
    }

    /**
     * Marks an order as collected in the local DB.
     * Called after successful physical pickup (door closed).
     */
    suspend fun markCollected(trackingNumber: String) {
        try {
            db.offlineCollections().markCollected(trackingNumber)
            Log.d(TAG, "Marked $trackingNumber as collected offline")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark $trackingNumber as collected", e)
        }
    }

    /**
     * Purges collected entries older than [retentionDays] days.
     * Called by LockerSyncWorker during scheduled sync.
     */
    suspend fun purgeOldCollected(retentionDays: Int = 7) {
        val cutoff = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        try {
            db.offlineCollections().purgeCollected(cutoff)
            Log.d(TAG, "Purged collected offline entries older than $retentionDays days")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to purge collected offline entries", e)
        }
    }
}
