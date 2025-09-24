package com.blitztech.pudokiosk.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure preferences manager using EncryptedSharedPreferences
 */
class Prefs(context: Context) {

    companion object {
        private const val PREFS_NAME = "zimpudo_kiosk_prefs"
        private const val KEY_LOCALE = "locale"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_TYPE = "user_type"
        private const val KEY_USER_MOBILE = "user_mobile"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_LAST_LOGIN = "last_login"
        private const val KEY_BAUD_RATE = "scanner_baud"
    }

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Language/Locale preferences
    fun setLocale(locale: String) {
        prefs.edit().putString(KEY_LOCALE, locale).apply()
    }

    fun getLocale(): String {
        return prefs.getString(KEY_LOCALE, "en") ?: "en"
    }

    // Scanner baud (default Honeywell often 115200)
    fun getScannerBaud(): Int = prefs.getInt(KEY_BAUD_RATE, 115200)
    fun setScannerBaud(v: Int) {
        prefs.edit().putInt(KEY_BAUD_RATE, v).apply()
    }

    // First launch tracking
    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchCompleted() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    // Onboarding completion
    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
    }

    // Authentication data
    fun saveAuthData(accessToken: String, refreshToken: String, userType: String, mobileNumber: String, userName: String? = null) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER_TYPE, userType)
            .putString(KEY_USER_MOBILE, mobileNumber)
            .putString(KEY_USER_NAME, userName)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putLong(KEY_LAST_LOGIN, System.currentTimeMillis())
            .apply()
    }

    fun getAccessToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    fun getRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    fun getUserType(): String? {
        return prefs.getString(KEY_USER_TYPE, null)
    }

    fun getUserMobile(): String? {
        return prefs.getString(KEY_USER_MOBILE, null)
    }

    fun getUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun getLastLogin(): Long {
        return prefs.getLong(KEY_LAST_LOGIN, 0)
    }

    fun clearAuthData() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_TYPE)
            .remove(KEY_USER_MOBILE)
            .remove(KEY_USER_NAME)
            .remove(KEY_IS_LOGGED_IN)
            .remove(KEY_LAST_LOGIN)
            .apply()
    }

    // Session management
    fun isSessionValid(): Boolean {
        if (!isLoggedIn()) return false

        val lastLogin = getLastLogin()
        val sessionTimeout = 24 * 60 * 60 * 1000L // 24 hours

        return (System.currentTimeMillis() - lastLogin) < sessionTimeout
    }

    fun extendSession() {
        if (isLoggedIn()) {
            prefs.edit().putLong(KEY_LAST_LOGIN, System.currentTimeMillis()).apply()
        }
    }

    // General utility methods
    fun clear() {
        prefs.edit().clear().apply()
    }

    fun clearAll() {
        clear()
    }

    // Custom preference methods for specific values
    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return prefs.getInt(key, defaultValue)
    }

    fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return prefs.getLong(key, defaultValue)
    }

    // Kiosk-specific settings
    fun setKioskMode(enabled: Boolean) {
        putBoolean("kiosk_mode", enabled)
    }

    fun isKioskMode(): Boolean {
        return getBoolean("kiosk_mode", true) // Default to true for kiosk app
    }

    fun setDeviceName(name: String) {
        putString("device_name", name)
    }

    fun getDeviceName(): String {
        return getString("device_name", "ZIMPUDO Kiosk")
    }

    fun setLocationId(locationId: String) {
        putString("location_id", locationId)
    }

    fun getLocationId(): String {
        return getString("location_id", "")
    }
}