package com.blitztech.pudokiosk.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local representation of a locker cell synced from the backend.
 * Table: cells
 *
 * The cellUuid comes from the backend Cell entity's ID (UUID).
 * physicalDoorNumber maps to the RS485 command address (1-20).
 */
@Entity(
    tableName = "cells",
    indices = [
        Index(value = ["lockerUuid"]),
        Index(value = ["status"]),
        Index(value = ["physicalDoorNumber", "lockerUuid"], unique = true)
    ]
)
data class CellEntity(
    @PrimaryKey val cellUuid: String,      // Backend UUID
    val lockerUuid: String,                // Parent locker UUID
    val physicalDoorNumber: Int,           // RS485 address (1-20 per board)
    val cellSize: String,                  // XS / S / M / L / XL
    val status: String,                    // AVAILABLE / OCCUPIED / RESERVED / MAINTENANCE / UNKNOWN
    val cabinetId: String? = null,         // Board identifier e.g. "CAB-001"
    val lastSyncedAt: Long = System.currentTimeMillis()
)
