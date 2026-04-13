package com.blitztech.pudokiosk.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Offline collection entry pre-synced from the backend.
 * Populated by LockerSyncWorker.
 *
 * Stores the bcrypt-hashed OTP so the kiosk can validate locally
 * when there's no network connectivity (Phase 4: Offline OTP validation).
 *
 * TODO [P0 BACKEND]: Requires GET /api/v1/orders/pending-collections?lockerId=...
 * to be implemented on the backend. This endpoint should return all orders in
 * AWAITING_COLLECTION status for this kiosk, with their bcrypt-hashed OTPs.
 */
@Entity(
    tableName = "offline_collections",
    indices = [Index(value = ["trackingNumber"], unique = true)]
)
data class OfflineCollectionEntity(
    @PrimaryKey val trackingNumber: String,
    val hashedOtp: String,       // bcrypt hash of the OTP — used for offline validation
    val cellNumber: Int,         // Physical door number for RS485 unlock (1-based, varies per locker)
    val lockerUuid: String,      // Parent locker UUID
    val recipientName: String? = null,
    val collected: Boolean = false,
    val syncedAt: Long = System.currentTimeMillis()
)
