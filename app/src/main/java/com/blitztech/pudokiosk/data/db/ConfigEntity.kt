package com.blitztech.pudokiosk.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "config")
data class ConfigEntity(
    @PrimaryKey val key: String = "device",
    val version: Long,
    val json: String,
    val updatedAt: Long
)
