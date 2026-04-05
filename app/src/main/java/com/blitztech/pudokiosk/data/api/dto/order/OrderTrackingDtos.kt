package com.blitztech.pudokiosk.data.api.dto.order

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ─────────────────────────────────────────────────────────────
//  Order Service – Order Tracking DTOs
//  Used for: GET /api/v1/orders/logged-in   (PageOrder)
//            GET /api/v1/orders/track/{trackingNumber}
// ─────────────────────────────────────────────────────────────

/**
 * Individual order model returned by the Order Service.
 */
@JsonClass(generateAdapter = true)
data class OrderDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "trackingNumber") val trackingNumber: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "senderName") val senderName: String? = null,
    @Json(name = "senderMobileNumber") val senderMobileNumber: String? = null,
    @Json(name = "recipientName") val recipientName: String? = null,
    @Json(name = "recipientMobileNumber") val recipientMobileNumber: String? = null,
    @Json(name = "lockerId") val lockerId: String? = null,
    @Json(name = "cellNumber") val cellNumber: Int? = null,
    @Json(name = "parcelSize") val parcelSize: String? = null,
    @Json(name = "currency") val currency: String? = null,
    @Json(name = "amount") val amount: Double? = null,
    @Json(name = "paymentStatus") val paymentStatus: String? = null,
    @Json(name = "createdAt") val createdAt: String? = null,
    @Json(name = "updatedAt") val updatedAt: String? = null
)

/**
 * Paginated order response from GET /api/v1/orders/logged-in.
 * Follows Spring Boot Page<T> structure.
 */
@JsonClass(generateAdapter = true)
data class PageOrder(
    @Json(name = "content") val content: List<OrderDto> = emptyList(),
    @Json(name = "totalElements") val totalElements: Long = 0,
    @Json(name = "totalPages") val totalPages: Int = 0,
    @Json(name = "number") val page: Int = 0,
    @Json(name = "size") val size: Int = 20,
    @Json(name = "first") val first: Boolean = true,
    @Json(name = "last") val last: Boolean = true
)

/**
 * Represents a single transit or event tracking entry from the backend.
 * Returned by GET /api/v1/orders/track/{trackingNumber}
 */
@JsonClass(generateAdapter = true)
data class OrderTrackerDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "trackingNumber") val trackingNumber: String? = null,
    @Json(name = "activity") val activity: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "createdDate") val createdDate: String? = null,
    @Json(name = "createdBy") val createdBy: String? = null
)

/**
 * Paginated tracking history list.
 */
@JsonClass(generateAdapter = true)
data class PageOrderTrackerDto(
    @Json(name = "content") val content: List<OrderTrackerDto> = emptyList(),
    @Json(name = "totalElements") val totalElements: Long = 0,
    @Json(name = "totalPages") val totalPages: Int = 0,
    @Json(name = "number") val page: Int = 0,
    @Json(name = "size") val size: Int = 20
)
