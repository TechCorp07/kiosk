package com.blitztech.pudokiosk.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events_outbox")
data class OutboxEventEntity(
    @PrimaryKey val idempotencyKey: String,
    val type: String,
    val payloadJson: String,
    val createdAt: Long,
    val delivered: Boolean = false
)
