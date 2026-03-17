package com.blitztech.pudokiosk.data.api.dto.common

import org.junit.Assert.*
import org.junit.Test

class AuthStatusTest {

    @Test
    fun pendingOtp_hasCorrectValue() {
        assertEquals("PENDING_OTP", AuthStatus.PENDING_OTP)
    }

    @Test
    fun authenticated_hasCorrectValue() {
        assertEquals("AUTHENTICATED", AuthStatus.AUTHENTICATED)
    }

    @Test
    fun failed_hasCorrectValue() {
        assertEquals("FAILED", AuthStatus.FAILED)
    }

    @Test
    fun allConstants_areDistinct() {
        val values = setOf(AuthStatus.PENDING_OTP, AuthStatus.AUTHENTICATED, AuthStatus.FAILED)
        assertEquals(3, values.size)
    }
}
