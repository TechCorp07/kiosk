package com.blitztech.pudokiosk.data.api.dto.collection

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ─────────────────────────────────────────────────────────────
//  Order Service – Locker Operations DTOs
//  Used for: /api/v1/locker/recipient/auth
//            /api/v1/locker/pickup
//            /api/v1/locker/open
// ─────────────────────────────────────────────────────────────

/**
 * Request to authenticate a recipient with their collection code.
 * POST /api/v1/locker/recipient/auth
 */
@JsonClass(generateAdapter = true)
data class RecipientAuthRequest(
    @Json(name = "collectionCode") val collectionCode: String,
    @Json(name = "kioskId") val kioskId: String
)

/**
 * Response from recipient authentication.
 * Contains the locker/cell assignment for opening the right door.
 */
@JsonClass(generateAdapter = true)
data class RecipientAuthResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String,
    @Json(name = "orderId") val orderId: String? = null,
    @Json(name = "lockerId") val lockerId: String? = null,
    @Json(name = "cellNumber") val cellNumber: Int? = null,
    @Json(name = "recipientName") val recipientName: String? = null,
    @Json(name = "parcelSize") val parcelSize: String? = null
)

/**
 * Request to open a specific locker cell.
 * POST /api/v1/locker/open
 */
@JsonClass(generateAdapter = true)
data class LockerOpenRequest(
    @Json(name = "lockerId") val lockerId: String,
    @Json(name = "cellNumber") val cellNumber: Int
)

/**
 * Request to confirm a recipient pickup.
 * POST /api/v1/locker/pickup
 */
@JsonClass(generateAdapter = true)
data class LockerPickupRequest(
    @Json(name = "orderId") val orderId: String,
    @Json(name = "kioskId") val kioskId: String
)
