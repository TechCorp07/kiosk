package com.blitztech.pudokiosk.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val phone: String,
    val email: String?,
    val role: String,            // USER | COURIER | TECHNICIAN
    val kycStatus: String,       // PENDING | VERIFIED
    val pinSet: Boolean,
    val updatedAt: Long
)
