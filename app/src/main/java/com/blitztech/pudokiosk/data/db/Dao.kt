package com.blitztech.pudokiosk.data.db

import androidx.room.*

@Dao
interface UsersDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(user: UserEntity)
    @Query("SELECT * FROM users WHERE id = :id") suspend fun get(id: String): UserEntity?
}

@Dao
interface ParcelsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(parcel: ParcelEntity)
    @Query("SELECT * FROM parcels WHERE id = :id") suspend fun get(id: String): ParcelEntity?
    @Query("SELECT * FROM parcels WHERE trackingCode = :code LIMIT 1")
    suspend fun getByTrackingCode(code: String): ParcelEntity?
    @Query("SELECT * FROM parcels WHERE status = :status ORDER BY createdAt DESC")
    suspend fun getByStatus(status: String): List<ParcelEntity>
    @Query("SELECT * FROM parcels WHERE lockNumber = :lockNumber LIMIT 1")
    suspend fun getByLockNumber(lockNumber: Int): ParcelEntity?
    @Query("SELECT * FROM parcels ORDER BY createdAt DESC")
    suspend fun getAll(): List<ParcelEntity>
}

@Dao
interface LockersDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(locker: LockerEntity)
    @Query("SELECT * FROM lockers WHERE id = :id") suspend fun get(id: String): LockerEntity?
    @Query("SELECT * FROM lockers") suspend fun getAll(): List<LockerEntity>
    @Query("SELECT * FROM lockers WHERE isClosed = 0 ORDER BY id ASC")
    suspend fun getAvailable(): List<LockerEntity>
    @Query("UPDATE lockers SET isClosed = :closed WHERE id = :id")
    suspend fun updateStatus(id: String, closed: Boolean)
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun enqueue(e: OutboxEventEntity)
    @Query("SELECT * FROM events_outbox WHERE delivered = 0 ORDER BY createdAt LIMIT :limit")
    suspend fun pending(limit: Int): List<OutboxEventEntity>
    @Query("UPDATE events_outbox SET delivered = 1 WHERE idempotencyKey IN (:keys)")
    suspend fun markDelivered(keys: List<String>)
}

@Dao
interface SecurityPhotoDao {
    @Insert suspend fun insert(photo: SecurityPhotoEntity): Long
    @Query("SELECT * FROM security_photos WHERE uploaded = 0 ORDER BY capturedAt LIMIT :limit")
    suspend fun getUnuploaded(limit: Int): List<SecurityPhotoEntity>
    @Query("UPDATE security_photos SET uploaded = 1 WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<Long>)
    @Query("DELETE FROM security_photos WHERE capturedAt < :epoch")
    suspend fun deleteOlderThan(epoch: Long)
}
