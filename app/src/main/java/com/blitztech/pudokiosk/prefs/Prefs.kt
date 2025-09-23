package com.blitztech.pudokiosk.prefs

import android.content.Context

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    // Language
    fun getLocale(): String = p.getString("locale", "en") ?: "en"
    fun setLocale(code: String) { p.edit().putString("locale", code).apply() }

    // Scanner baud (default Honeywell often 115200)
    fun getScannerBaud(): Int = p.getInt("scanner_baud", 115200)
    fun setScannerBaud(v: Int) { p.edit().putInt("scanner_baud", v).apply() }

    // Authentication tokens
    fun getAccessToken(): String = p.getString("access_token", "") ?: ""
    fun setAccessToken(token: String) { p.edit().putString("access_token", token).apply() }

    fun getRefreshToken(): String = p.getString("refresh_token", "") ?: ""
    fun setRefreshToken(token: String) { p.edit().putString("refresh_token", token).apply() }

    // User type
    fun getUserType(): String = p.getString("user_type", "") ?: ""
    fun setUserType(type: String) { p.edit().putString("user_type", type).apply() }

    // Check if user is logged in
    fun isLoggedIn(): Boolean = getAccessToken().isNotEmpty()

    // Clear all auth data
    fun clearAuthData() {
        p.edit().apply {
            remove("access_token")
            remove("refresh_token")
            remove("user_type")
        }.apply()
    }
}