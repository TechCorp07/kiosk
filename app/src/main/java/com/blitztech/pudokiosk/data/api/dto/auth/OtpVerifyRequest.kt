package com.blitztech.pudokiosk.data.api.dto.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OtpVerifyRequest(
    @Json(name = "mobileNumber") val mobileNumber: String,
    @Json(name = "otp") val otp: String
)

@JsonClass(generateAdapter = true)
data class OtpVerifyResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "status") val status: String // AUTHENTICATED, FAILED
)