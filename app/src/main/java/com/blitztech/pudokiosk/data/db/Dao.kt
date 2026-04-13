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
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(e: OutboxEventEntity)
    @Query("SELECT * FROM events_outbox WHERE delivered = 0 ORDER BY createdAt LIMIT :limit")
    suspend fun pending(limit: Int): List<OutboxEventEntity>
    @Query("UPDATE events_outbox SET delivered = 1 WHERE idempotencyKey IN (:keys)")
    suspend fun markDelivered(keys: List<String>)
    @Query("DELETE FROM events_outbox WHERE delivered = 1 AND createdAt < :cutoffEpochMs")
    suspend fun deleteDeliveredBefore(cutoffEpochMs: Long)
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

/**
 * CellsDao — manages local cell inventory synced from the locker service.
 * Cells are synced during heartbeat (LockerSyncWorker) and used for
 * offline cell assignment during courier dropoff.
 */
@Dao
interface CellsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(cells: List<CellEntity>)

    @Query("SELECT * FROM cells WHERE lockerUuid = :lockerUuid AND status = 'AVAILABLE' ORDER BY physicalDoorNumber ASC LIMIT 1")
    suspend fun getNextAvailableCell(lockerUuid: String): CellEntity?

    @Query("SELECT * FROM cells WHERE lockerUuid = :lockerUuid AND status = 'AVAILABLE' ORDER BY physicalDoorNumber ASC")
    suspend fun getAvailableCells(lockerUuid: String): List<CellEntity>

    @Query("SELECT * FROM cells WHERE physicalDoorNumber = :doorNumber LIMIT 1")
    suspend fun getCellByDoorNumber(doorNumber: Int): CellEntity?

    @Query("SELECT * FROM cells WHERE cellUuid = :cellUuid LIMIT 1")
    suspend fun getCellByUuid(cellUuid: String): CellEntity?

    @Query("UPDATE cells SET status = 'OCCUPIED' WHERE cellUuid = :cellUuid")
    suspend fun markCellOccupied(cellUuid: String)

    @Query("UPDATE cells SET status = 'AVAILABLE' WHERE cellUuid = :cellUuid")
    suspend fun markCellAvailable(cellUuid: String)

    @Query("SELECT COUNT(*) FROM cells WHERE lockerUuid = :lockerUuid AND status = 'AVAILABLE'")
    suspend fun countAvailable(lockerUuid: String): Int

    @Query("SELECT * FROM cells WHERE lockerUuid = :lockerUuid ORDER BY physicalDoorNumber ASC")
    suspend fun getAllForLocker(lockerUuid: String): List<CellEntity>

    @Query("DELETE FROM cells WHERE lockerUuid = :lockerUuid")
    suspend fun deleteAllForLocker(lockerUuid: String)
}

/**
 * OfflineCollectionDao — stores pre-synced collection data for offline OTP validation.
 * Populated by LockerSyncWorker from the backend's pending-collections endpoint.
 */
@Dao
interface OfflineCollectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: OfflineCollectionEntity)

    @Query("SELECT * FROM offline_collections WHERE trackingNumber = :trackingNumber LIMIT 1")
    suspend fun getByTracking(trackingNumber: String): OfflineCollectionEntity?

    @Query("UPDATE offline_collections SET collected = 1 WHERE trackingNumber = :trackingNumber")
    suspend fun markCollected(trackingNumber: String)

    @Query("DELETE FROM offline_collections WHERE collected = 1 AND syncedAt < :epoch")
    suspend fun purgeCollected(epoch: Long)

    @Query("SELECT * FROM offline_collections WHERE collected = 0")
    suspend fun getAllPending(): List<OfflineCollectionEntity>
}
