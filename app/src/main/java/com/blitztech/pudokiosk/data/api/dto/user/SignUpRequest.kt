package com.blitztech.pudokiosk.data.api.dto.user

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Address(
    @Json(name = "city") val city: String,
    @Json(name = "suburb") val suburb: String,
    @Json(name = "street") val street: String,
    @Json(name = "houseNumber") val houseNumber: String
)

@JsonClass(generateAdapter = true)
data class SignUpRequest(
    @Json(name = "name") val name: String,
    @Json(name = "surname") val surname: String,
    @Json(name = "email") val email: String,
    @Json(name = "mobileNumber") val mobileNumber: String,
    @Json(name = "nationalId") val nationalId: String,
    @Json(name = "address") val address: Address,
    @Json(name = "role") val role: String = "USER"
)

@JsonClass(generateAdapter = true)
data class KycUploadRequest(
    @Json(name = "type") val type: String = "NATIONAL_ID",
    @Json(name = "file") val file: String
)