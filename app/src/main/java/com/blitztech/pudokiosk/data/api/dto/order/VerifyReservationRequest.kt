package com.blitztech.pudokiosk.data.api.dto.order

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Must match backend AccessMethod.java exactly: QR_CODE, PIN_CODE.
 * Sending any other value causes a 400 deserialization error.
 */
enum class AccessMethod {
    QR_CODE,
    PIN_CODE
}

@JsonClass(generateAdapter = true)
data class VerifyReservationRequest(
    @Json(name = "accessMethod") val accessMethod: AccessMethod,
    @Json(name = "reservationInformation") val reservationInformation: Map<String, String>
)
