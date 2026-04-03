package com.blitztech.pudokiosk.auth

import android.util.Base64
import android.util.Log
import com.blitztech.pudokiosk.prefs.Prefs
import org.json.JSONObject

/**
 * TokenManager — JWT lifecycle management for the kiosk.
 *
 * Handles:
 *   - Decoding JWT expiry without a library (Base64 + JSONObject)
 *   - Proactive refresh when token is within 5 minutes of expiry
 *   - Device-level CLIENT_CREDENTIALS flow (Phase 2 — TODO: backend endpoint)
 *
 * TODO [PHASE 2]: Implement getOrRefreshDeviceToken() once backend exposes:
 *   POST /api/v1/auth/device-token
 *   Body: { deviceId, clientSecret }
 *   Response: { accessToken, expiresIn }
 */
object TokenManager {

    private const val TAG = "TokenManager"
    private const val REFRESH_BUFFER_MS = 5 * 60 * 1000L // 5 minutes before expiry

    /**
     * Parses the expiry time from a JWT token's payload.
     * Returns the epoch-millisecond timestamp of expiry, or 0 if parsing fails.
     */
    fun parseExpiryMs(jwt: String): Long {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return 0L

            // Add padding if needed (Base64 standard requires multiples of 4)
            val payload = parts[1].let { p ->
                val padding = (4 - p.length % 4) % 4
                p + "=".repeat(padding)
            }

            val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP)
            val json = JSONObject(String(decoded, Charsets.UTF_8))
            val exp = json.optLong("exp", 0L)
            exp * 1000L // convert seconds → milliseconds
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JWT expiry: ${e.message}")
            0L
        }
    }

    /**
     * Checks whether the stored token has expired or expires within the buffer window.
     */
    fun isTokenValid(prefs: Prefs): Boolean {
        val token = prefs.getAccessToken() ?: return false
        if (token.isBlank()) return false

        // Check stored expiry first (fast path — avoids parsing every call)
        val storedExpiry = prefs.getTokenExpiresAt()
        if (storedExpiry > 0L) {
            return System.currentTimeMillis() + REFRESH_BUFFER_MS < storedExpiry
        }

        // Parse from JWT if expiry not cached yet
        val expiry = parseExpiryMs(token)
        if (expiry > 0L) {
            prefs.setTokenExpiresAt(expiry)
        }
        return System.currentTimeMillis() + REFRESH_BUFFER_MS < expiry
    }

    /**
     * Caches the expiry from a newly received JWT token.
     * Call this after every successful login or token refresh.
     */
    fun onTokenReceived(prefs: Prefs, jwt: String) {
        val expiry = parseExpiryMs(jwt)
        if (expiry > 0L) {
            prefs.setTokenExpiresAt(expiry)
            Log.d(TAG, "Token expiry cached: ${java.util.Date(expiry)}")
        } else {
            Log.w(TAG, "Could not parse token expiry — treating as valid for now")
        }
    }

    /**
     * Returns a human-readable string describing the remaining token validity.
     * Useful for debug/technician screens.
     */
    fun getValidityDescription(prefs: Prefs): String {
        val expiresAt = prefs.getTokenExpiresAt()
        if (expiresAt == 0L) return "Token expiry unknown"

        val remaining = expiresAt - System.currentTimeMillis()
        return when {
            remaining < 0 -> "Token EXPIRED ${-remaining / 60000} minutes ago"
            remaining < 60_000 -> "Token expires in ${remaining / 1000}s"
            else -> "Token expires in ${remaining / 60000}m"
        }
    }
}
