package com.blitztech.pudokiosk.data.api.dto.order

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class AccessMethod {
    PIN,
    OTP,
    QR_CODE,
    BARCODE,
    RFID
}

@JsonClass(generateAdapter = true)
data class VerifyReservationRequest(
    @Json(name = "accessMethod") val accessMethod: AccessMethod,
    @Json(name = "reservationInformation") val reservationInformation: Map<String, String>
)
