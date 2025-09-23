package com.blitztech.pudokiosk.data.api.dto.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PinChangeRequest(
    @Json(name = "oldPin") val oldPin: String,
    @Json(name = "newPin") val newPin: String
)

@JsonClass(generateAdapter = true)
data class ForgotPinRequest(
    @Json(name = "mobileNumber") val mobileNumber: String,
    @Json(name = "otpMethod") val otpMethod: String = "SMS_EMAIL"
)
