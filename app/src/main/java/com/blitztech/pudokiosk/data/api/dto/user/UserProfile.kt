package com.blitztech.pudokiosk.data.api.dto.user

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UserProfileDto(
    @Json(name = "name") val name: String?,
    @Json(name = "surname") val surname: String?,
    @Json(name = "mobileNumber") val mobileNumber: String?,
    @Json(name = "email") val email: String?,
    @Json(name = "nationalId") val nationalId: String?,
    @Json(name = "address") val address: ProfileAddressDto?,
    @Json(name = "role") val role: String?,
    @Json(name = "kycStatus") val kycStatus: String?
)

@JsonClass(generateAdapter = true)
data class ProfileAddressDto(
    @Json(name = "city") val city: String?,
    @Json(name = "suburb") val suburb: String?,
    @Json(name = "street") val street: String?,
    @Json(name = "houseNumber") val houseNumber: String?
)

@JsonClass(generateAdapter = true)
data class UserProfileUpdateRequest(
    @Json(name = "name") val name: String,
    @Json(name = "surname") val surname: String,
    @Json(name = "email") val email: String,
    @Json(name = "nationalId") val nationalId: String,
    @Json(name = "address") val address: ProfileAddressDto
)
