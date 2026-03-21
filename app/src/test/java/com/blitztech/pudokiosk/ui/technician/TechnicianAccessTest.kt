package com.blitztech.pudokiosk.ui.technician

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for technician login credential validation logic.
 *
 * Extracted from TechnicianAccessActivity for testability — we mirror
 * the exact credentials map and validation rules from the activity.
 */
class TechnicianAccessTest {

    // Mirror of the credentials map in TechnicianAccessActivity
    private val validCredentials = mapOf(
        "tech001"     to "admin123",
        "service"     to "service2024",
        "maintenance" to "maintain456"
    )

    /** Simulates attemptTechnicianLogin() — returns true on success. */
    private fun validateLogin(username: String, password: String): Boolean {
        if (username.isEmpty() || password.isEmpty()) return false
        return validCredentials[username] == password
    }

    // ─── Valid credential pairs ──────────────────────────────────────────────

    @Test
    fun validCredentials_tech001_shouldPass() {
        assertTrue(validateLogin("tech001", "admin123"))
    }

    @Test
    fun validCredentials_service_shouldPass() {
        assertTrue(validateLogin("service", "service2024"))
    }

    @Test
    fun validCredentials_maintenance_shouldPass() {
        assertTrue(validateLogin("maintenance", "maintain456"))
    }

    // ─── Invalid credentials ─────────────────────────────────────────────────

    @Test
    fun wrongPassword_shouldFail() {
        assertFalse(validateLogin("tech001", "wrongpassword"))
    }

    @Test
    fun wrongUsername_shouldFail() {
        assertFalse(validateLogin("admin", "admin123"))
    }

    @Test
    fun swappedCredentials_shouldFail() {
        assertFalse(validateLogin("admin123", "tech001"))
    }

    @Test
    fun partialUsername_shouldFail() {
        assertFalse(validateLogin("tech", "admin123"))
    }

    @Test
    fun partialPassword_shouldFail() {
        assertFalse(validateLogin("tech001", "admin"))
    }

    @Test
    fun caseSensitiveUsername_shouldFail() {
        assertFalse(validateLogin("Tech001", "admin123"))
    }

    @Test
    fun caseSensitivePassword_shouldFail() {
        assertFalse(validateLogin("tech001", "Admin123"))
    }

    // ─── Empty / blank input ─────────────────────────────────────────────────

    @Test
    fun emptyUsername_shouldFail() {
        assertFalse(validateLogin("", "admin123"))
    }

    @Test
    fun emptyPassword_shouldFail() {
        assertFalse(validateLogin("tech001", ""))
    }

    @Test
    fun bothEmpty_shouldFail() {
        assertFalse(validateLogin("", ""))
    }

    @Test
    fun whitespaceUsername_shouldFail() {
        // The activity trims input, so " " becomes "" which is rejected
        val trimmed = " ".trim()
        assertFalse(validateLogin(trimmed, "admin123"))
    }

    @Test
    fun whitespacePassword_shouldFail() {
        val trimmed = " ".trim()
        assertFalse(validateLogin("tech001", trimmed))
    }

    // ─── Credentials map structure ───────────────────────────────────────────

    @Test
    fun credentialsMap_hasExactlyThreeEntries() {
        assertEquals(3, validCredentials.size)
    }

    @Test
    fun credentialsMap_containsExpectedUsernames() {
        assertTrue(validCredentials.containsKey("tech001"))
        assertTrue(validCredentials.containsKey("service"))
        assertTrue(validCredentials.containsKey("maintenance"))
    }

    @Test
    fun credentialsMap_doesNotContainUnexpectedKeys() {
        assertFalse(validCredentials.containsKey("admin"))
        assertFalse(validCredentials.containsKey("root"))
        assertFalse(validCredentials.containsKey("technician"))
    }
}
