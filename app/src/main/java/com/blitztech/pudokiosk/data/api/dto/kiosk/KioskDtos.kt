package com.blitztech.pudokiosk.data.api.dto.kiosk

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Request DTO for POST /api/v1/kiosks/provision.
 * Sent during first-boot provisioning.
 */
@JsonClass(generateAdapter = true)
data class KioskProvisionRequest(
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "lockerShortCodes") val lockerShortCodes: List<String>,
    @Json(name = "siteName") val siteName: String,
    @Json(name = "provisioningCode") val provisioningCode: String,
    @Json(name = "androidVersion") val androidVersion: String? = null,
    @Json(name = "appVersion") val appVersion: String? = null,
    @Json(name = "serialNumber") val serialNumber: String? = null
)

/**
 * Response DTO from POST /api/v1/kiosks/provision.
 */
@JsonClass(generateAdapter = true)
data class KioskProvisionResponse(
    @Json(name = "kioskId") val kioskId: String,
    @Json(name = "siteName") val siteName: String,
    @Json(name = "lockerCount") val lockerCount: Int,
    @Json(name = "totalCells") val totalCells: Int,
    @Json(name = "lockers") val lockers: List<LockerDetail>
)

@JsonClass(generateAdapter = true)
data class LockerDetail(
    @Json(name = "lockerId") val lockerId: String,
    @Json(name = "shortCode") val shortCode: String,
    @Json(name = "name") val name: String,
    @Json(name = "cellCount") val cellCount: Int,
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null
)

/**
 * Backend wraps all responses in ApiResponseWrapper { success, message, body }.
 * This typed wrapper extracts the provisioning response from the body field.
 */
@JsonClass(generateAdapter = true)
data class KioskProvisionApiResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String,
    @Json(name = "body") val body: KioskProvisionResponse? = null
)
