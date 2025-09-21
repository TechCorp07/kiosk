package com.blitztech.pudokiosk.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parcels")
data class ParcelEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val recipientId: String,
    val size: String,            // XS | S | M | L | XL
    val status: String,          // CREATED | IN_LOCKER | READY_FOR_PICKUP | COLLECTED | EXCEPTION
    val lockerId: String?,
    val trackingCode: String,
    val createdAt: Long
)
