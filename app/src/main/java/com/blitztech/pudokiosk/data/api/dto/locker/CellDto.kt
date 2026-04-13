package com.blitztech.pudokiosk.data.api.dto.locker

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Cell DTO returned by GET /api/v1/lockers/{lockerId}/cells.
 * Used to sync the locker's physical cell inventory into the local Room DB.
 *
 * The backend [Cell] entity contains:
 *   id (UUID), lockerId (UUID), number (Int, 1-based), size (CellSize), status (CellStatus)
 *
 * Option A: server also returns cellNumber (physical door number) in collection responses.
 */
@JsonClass(generateAdapter = true)
data class CellDto(
    @Json(name = "id") val id: String,                  // UUID — backend primary key
    @Json(name = "lockerId") val lockerId: String,       // UUID of parent locker
    @Json(name = "cellNumber") val cellNumber: Int,              // Physical door number (1-based, varies per locker)
    @Json(name = "size") val size: String,               // XS / S / M / L / XL
    @Json(name = "status") val status: String,           // AVAILABLE / OCCUPIED / RESERVED / MAINTENANCE
    @Json(name = "cabinetId") val cabinetId: String? = null  // Board identifier e.g. "CAB-001"
)

/**
 * Backend wraps the cell list in ApiResponseWrapper { success, message, body }.
 */
@JsonClass(generateAdapter = true)
data class KioskCellsApiResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String,
    @Json(name = "body") val body: List<CellDto>? = null
)
