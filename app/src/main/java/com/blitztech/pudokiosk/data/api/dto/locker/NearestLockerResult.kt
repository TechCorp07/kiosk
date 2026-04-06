package com.blitztech.pudokiosk.data.api.dto.locker

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Matches backend LockerResponseWithDistance { lockerResponse, distance }.
 * Returned by GET /api/v1/lockers/nearest/multiple.
 */
@JsonClass(generateAdapter = true)
data class NearestLockerResult(
    @Json(name = "lockerResponse") val lockerResponse: LockerInfo? = null,
    @Json(name = "distance") val distance: Double? = null
)

@JsonClass(generateAdapter = true)
data class LockerInfo(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "shortCode") val shortCode: String? = null,
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "lockerStatus") val lockerStatus: String? = null,
    @Json(name = "lastHeartbeat") val lastHeartbeat: String? = null,
    @Json(name = "kioskId") val kioskId: String? = null
)

/**
 * Wrapper matching backend ApiResponseWrapper { success, message, body }.
 */
@JsonClass(generateAdapter = true)
data class NearestLockersApiResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "message") val message: String? = null,
    @Json(name = "body") val body: List<NearestLockerResult>? = null
)
