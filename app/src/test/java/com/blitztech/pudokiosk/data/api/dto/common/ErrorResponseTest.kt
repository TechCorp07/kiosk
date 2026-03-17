package com.blitztech.pudokiosk.data.api.dto.common

import org.junit.Assert.*
import org.junit.Test

class ErrorResponseTest {

    @Test
    fun getUserFriendlyMessage_withErrors_returnsJoinedErrors() {
        val response = ErrorResponse(
            success = false,
            message = "Validation failed",
            errors = mapOf("email" to "Invalid email", "phone" to "Required")
        )
        val msg = response.getUserFriendlyMessage()
        assertTrue(msg.contains("Invalid email"))
        assertTrue(msg.contains("Required"))
    }

    @Test
    fun getUserFriendlyMessage_withEmptyErrors_returnsMessage() {
        val response = ErrorResponse(
            success = false,
            message = "Something went wrong",
            errors = emptyMap()
        )
        assertEquals("Something went wrong", response.getUserFriendlyMessage())
    }

    @Test
    fun getUserFriendlyMessage_withNullErrors_returnsMessage() {
        val response = ErrorResponse(
            success = false,
            message = "Server error",
            errors = null
        )
        assertEquals("Server error", response.getUserFriendlyMessage())
    }

    @Test
    fun getUserFriendlyMessage_blankMessage_noErrors_returnsDefault() {
        val response = ErrorResponse(
            success = false,
            message = "",
            errors = null
        )
        assertEquals("An error occurred. Please try again.", response.getUserFriendlyMessage())
    }

    @Test
    fun getUserFriendlyMessage_blankMessage_emptyErrors_returnsDefault() {
        val response = ErrorResponse(
            success = false,
            message = "   ",
            errors = emptyMap()
        )
        // "   " is not blank (isNotBlank returns true for whitespace-only)
        // Actually "   ".isNotBlank() returns false in Kotlin
        assertEquals("An error occurred. Please try again.", response.getUserFriendlyMessage())
    }

    @Test
    fun errorResponse_instantiation() {
        val response = ErrorResponse(true, "OK")
        assertTrue(response.success)
        assertEquals("OK", response.message)
        assertNull(response.errors)
    }
}
