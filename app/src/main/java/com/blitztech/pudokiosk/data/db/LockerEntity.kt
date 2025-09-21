package com.blitztech.pudokiosk.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lockers")
data class LockerEntity(
    @PrimaryKey val id: String,
    val size: String,
    val isClosed: Boolean,
    val lastOpenAt: Long?,
    val lastErrorCode: String?
)
