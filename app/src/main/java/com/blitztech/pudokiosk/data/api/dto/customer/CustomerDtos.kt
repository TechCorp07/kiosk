package com.blitztech.pudokiosk.data.api.dto.customer

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs for customer-facing parcel queries.
 */
@JsonClass(generateAdapter = true)
data class CustomerParcel(
    @Json(name = "parcelId")   val parcelId: String,
    @Json(name = "trackingCode") val trackingCode: String,
    @Json(name = "status")     val status: String,
    @Json(name = "lockNumber") val lockNumber: Int?,
    @Json(name = "parcelSize") val parcelSize: String,
    @Json(name = "senderName") val senderName: String?,
    @Json(name = "updatedAt")  val updatedAt: String
)
