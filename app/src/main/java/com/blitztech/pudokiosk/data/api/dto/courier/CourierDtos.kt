package com.blitztech.pudokiosk.data.api.dto.courier

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ─────────────────────────────────────────────────────────────
//  Locker Service – Transaction DTOs
//  Used for: /api/v1/transactions/courier/pickup
//            /api/v1/transactions/courier/dropoff
// ─────────────────────────────────────────────────────────────

/**
 * Request body for courier transaction endpoints.
 * The courier scans a barcode → kiosk builds this request.
 */
@JsonClass(generateAdapter = true)
data class TransactionRequest(
    @Json(name = "trackingNumber") val trackingNumber: String,
    @Json(name = "kioskId") val kioskId: String,
    @Json(name = "lockerId") val lockerId: String? = null,
    @Json(name = "cellNumber") val cellNumber: Int? = null
)

/**
 * Response from courier transaction endpoints.
 * Contains the locker/cell assignment so the kiosk can open the right door.
 */
@JsonClass(generateAdapter = true)
data class TransactionResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String,
    @Json(name = "transactionId") val transactionId: String? = null,
    @Json(name = "orderId") val orderId: String? = null,
    @Json(name = "lockerId") val lockerId: String? = null,
    @Json(name = "cellId") val cellId: String? = null,
    @Json(name = "cellNumber") val cellNumber: Int? = null,
    @Json(name = "trackingNumber") val trackingNumber: String? = null,
    @Json(name = "recipientName") val recipientName: String? = null,
    @Json(name = "status") val status: String? = null
)

// ─────────────────────────────────────────────────────────────
//  Orders Service – Courier dropoff at destination PUDO locker
//  POST /api/v1/orders/{orderId}/dropoff?barcode=...&destinationLockerId=...
//  Returns GenericResponse { success, message }
// ─────────────────────────────────────────────────────────────

/**
 * Generic response from orders-service courier operations.
 * Used for dropoff and pickup-scan endpoints.
 */
@JsonClass(generateAdapter = true)
data class CourierOpsResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String
)

// ─────────────────────────────────────────────────────────────
//  Orders Service – Order search result (used to resolve orderId by barcode)
//  POST /api/v1/orders/and-search  { "trackingNumber": "..." }
// ─────────────────────────────────────────────────────────────

/**
 * Minimal order lookup result — we only need the orderId and status.
 */
@JsonClass(generateAdapter = true)
data class OrderLookupResult(
    @Json(name = "id") val orderId: String? = null,
    @Json(name = "trackingNumber") val trackingNumber: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "cabinetId") val cabinetId: String? = null,
    @Json(name = "cellId") val cellId: String? = null,
    @Json(name = "cellNumber") val cellNumber: Int? = null  // Physical door number (Option A)
)

/**
 * Paged response wrapper for order search.
 */
@JsonClass(generateAdapter = true)
data class OrderSearchPage(
    @Json(name = "content") val content: List<OrderLookupResult> = emptyList(),
    @Json(name = "totalElements") val totalElements: Int = 0
)

// ─────────────────────────────────────────────────────────────
//  Legacy models kept for adapter compatibility
//  (Used by outbox SyncWorker deserialization)
// ─────────────────────────────────────────────────────────────

/**
 * Parcel model used by CourierParcelAdapter for display.
 * Populated from TransactionResponse fields.
 */
@JsonClass(generateAdapter = true)
data class CourierParcel(
    @Json(name = "parcelId") val parcelId: String,
    @Json(name = "orderId") val orderId: String,
    @Json(name = "lockNumber") val lockNumber: Int,
    @Json(name = "tracking") val tracking: String,
    @Json(name = "size") val size: String,
    @Json(name = "recipientName") val recipientName: String,
    @Json(name = "status") val status: String
)

/**
 * Barcode-based lookup request for kiosk operations.
 */
@JsonClass(generateAdapter = true)
data class ParcelLookupRequest(
    @Json(name = "barcode") val barcode: String,
    @Json(name = "kioskId") val kioskId: String
)
