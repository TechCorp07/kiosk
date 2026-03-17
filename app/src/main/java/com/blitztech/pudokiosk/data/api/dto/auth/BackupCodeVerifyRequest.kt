package com.blitztech.pudokiosk.data.api.dto.auth

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Backup code verification request – alternative to OTP.
 * Matches backend: BackupCodeVerificationRequest { mobileNumber, backupCode }
 */
@JsonClass(generateAdapter = true)
data class BackupCodeVerifyRequest(
    @Json(name = "mobileNumber") val mobileNumber: String,
    @Json(name = "backupCode") val backupCode: String
)
