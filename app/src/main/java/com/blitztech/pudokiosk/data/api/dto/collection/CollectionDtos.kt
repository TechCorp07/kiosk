package com.blitztech.pudokiosk.data.api.dto.collection

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ─────────────────────────────────────────────────────────────
//  Order Service – Locker Operations DTOs
//  Endpoints:
//    POST /api/v1/locker/recipient/auth  (public — no auth token)
//    POST /api/v1/locker/pickup          (public — no auth token)
//    POST /api/v1/locker/open            (authenticated)
// ─────────────────────────────────────────────────────────────

/**
 * Kiosk collection auth request.
 * Backend: LockerController.RecipientAuthRequest (kiosk flow).
 * Sends trackingNumber + OTP credential — backend resolves order internally.
 * POST /api/v1/locker/recipient/auth
 */
@JsonClass(generateAdapter = true)
data class RecipientAuthRequest(
    @Json(name = "trackingNumber") val trackingNumber: String,
    @Json(name = "credential") val credential: String,         // The OTP the recipient received via SMS
    @Json(name = "authenticationType") val authenticationType: String = "OTP"
)

/**
 * Response from kiosk recipient authentication.
 * Backend returns: CollectionValidationResponse (success, message, trackingNumber, cabinetId, cellId)
 * Option A: backend also includes cellNumber (physical door number, 1-based).
 */
@JsonClass(generateAdapter = true)
data class RecipientAuthResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String,
    @Json(name = "trackingNumber") val trackingNumber: String? = null,
    @Json(name = "cabinetId") val cabinetId: String? = null,   // Board/cabinet identifier e.g. "CAB-001"
    @Json(name = "cellId") val cellId: String? = null,         // UUID of the cell in the backend DB
    @Json(name = "cellNumber") val cellNumber: Int? = null      // Physical door number for RS485 unlock (Option A)
)

/**
 * Request to open a specific locker cell.
 * POST /api/v1/locker/open  (general purpose, authenticated)
 */
@JsonClass(generateAdapter = true)
data class LockerOpenRequest(
    @Json(name = "cabinetId") val cabinetId: String,
    @Json(name = "cellId") val cellId: String,
    @Json(name = "authenticationType") val authenticationType: String = "OTP",
    @Json(name = "credential") val credential: String = ""
)

/**
 * Kiosk collection confirmation request.
 * Backend: LockerController.PickupRequest (kiosk flow — uses trackingNumber).
 * POST /api/v1/locker/pickup
 */
@JsonClass(generateAdapter = true)
data class LockerPickupRequest(
    @Json(name = "trackingNumber") val trackingNumber: String,
    @Json(name = "authenticationType") val authenticationType: String = "OTP"
)
