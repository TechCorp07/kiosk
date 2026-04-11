package com.blitztech.pudokiosk.utils

import android.util.Base64
import org.json.JSONObject
import android.util.Log

object JwtUtils {
    private const val TAG = "JwtUtils"

    /**
     * Extracts the 'role' claim from a standard JWT token.
     * Returns null if parsing fails or role is missing.
     */
    fun extractRole(token: String?): String? {
        if (token.isNullOrBlank()) return null
        
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                // Decode the payload (second part of JWT)
                val payloadBase64 = parts[1]
                val decodedBytes = Base64.decode(payloadBase64, Base64.URL_SAFE)
                val payloadString = String(decodedBytes, Charsets.UTF_8)
                
                val jsonObject = JSONObject(payloadString)
                jsonObject.optString("role", null)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode JWT to extract role: ${e.message}")
            null
        }
    }
}
