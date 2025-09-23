package com.blitztech.pudokiosk.data.api.dto.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "mobileNumber") val mobileNumber: String,
    @Json(name = "pin") val pin: String,
    @Json(name = "otpMethod") val otpMethod: String = "SMS_EMAIL"
)