package com.blitztech.pudokiosk.data.api.dto.common

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ErrorResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String,
    @Json(name = "errors") val errors: Map<String, String>? = null
)

// Extension function to get user-friendly error message
fun ErrorResponse.getUserFriendlyMessage(): String {
    return when {
        errors?.isNotEmpty() == true -> {
            // Concatenate all error messages
            errors.values.joinToString(", ")
        }
        message.isNotBlank() -> message
        else -> "An error occurred. Please try again."
    }
}