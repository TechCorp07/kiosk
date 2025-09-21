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
}

@Dao
interface LockersDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(locker: LockerEntity)
    @Query("SELECT * FROM lockers WHERE id = :id") suspend fun get(id: String): LockerEntity?
}

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun enqueue(e: OutboxEventEntity)
    @Query("SELECT * FROM events_outbox WHERE delivered = 0 ORDER BY createdAt LIMIT :limit")
    suspend fun pending(limit: Int = 50): List<OutboxEventEntity>
    @Query("UPDATE events_outbox SET delivered = 1 WHERE idempotencyKey IN (:keys)")
    suspend fun markDelivered(keys: List<String>)
}
