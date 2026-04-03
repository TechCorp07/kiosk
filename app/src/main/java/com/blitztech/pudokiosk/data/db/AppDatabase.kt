package com.blitztech.pudokiosk.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        ParcelEntity::class,
        LockerEntity::class,
        CellEntity::class,
        OfflineCollectionEntity::class,
        OutboxEventEntity::class,
        AuditLogEntity::class,
        ConfigEntity::class,
        SecurityPhotoEntity::class
    ],
    version = 5
)

abstract class AppDatabase : RoomDatabase() {
    abstract fun users(): UsersDao
    abstract fun parcels(): ParcelsDao
    abstract fun lockers(): LockersDao
    abstract fun cells(): CellsDao
    abstract fun offlineCollections(): OfflineCollectionDao
    abstract fun outbox(): OutboxDao
    abstract fun config(): ConfigDao
    abstract fun audit(): AuditDao
    abstract fun securityPhotos(): SecurityPhotoDao
}
