package com.blitztech.pudokiosk.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val level: String, // INFO/WARN/ERROR
    val event: String,
    val details: String?
)
