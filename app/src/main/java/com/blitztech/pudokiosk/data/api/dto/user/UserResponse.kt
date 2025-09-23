package com.blitztech.pudokiosk.data.api.dto.common

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ApiResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String,
    @Json(name = "errors") val errors: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class RegistrationResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String
)

@JsonClass(generateAdapter = true)
data class KycResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String
)

// Auth status constants
object AuthStatus {
    const val PENDING_OTP = "PENDING_OTP"
    const val AUTHENTICATED = "AUTHENTICATED"
    const val FAILED = "FAILED"
}