package com.blitztech.pudokiosk.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserEntity::class,
        ParcelEntity::class,
        LockerEntity::class,
        OutboxEventEntity::class,
        AuditLogEntity::class,
        ConfigEntity::class
    ],
    version = 3
)

abstract class AppDatabase : RoomDatabase() {
    abstract fun users(): UsersDao
    abstract fun parcels(): ParcelsDao
    abstract fun lockers(): LockersDao
    abstract fun outbox(): OutboxDao
    abstract fun config(): ConfigDao
    abstract fun audit(): AuditDao
}
