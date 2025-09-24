package com.blitztech.pudokiosk.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * Secure preferences manager using EncryptedSharedPreferences with fallback support
 * Falls back to regular SharedPreferences if encrypted version fails (common in emulators)
 */
class Prefs(context: Context) {

    companion object {
        private const val TAG = "Prefs"
        private const val PREFS_NAME = "zimpudo_kiosk_prefs"
        private const val PREFS_NAME_FALLBACK = "zimpudo_kiosk_prefs_fallback"
        private const val ENCRYPTED_INIT_TIMEOUT_MS = 5000L // 5 seconds max

        // Keys
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
        private const val KEY_USING_ENCRYPTED = "using_encrypted_prefs"
    }

    private val prefs: SharedPreferences
    private val isEncrypted: Boolean

    init {
        var encryptedPrefs: SharedPreferences? = null
        var encrypted = false

        // Try to initialize encrypted preferences with timeout
        try {
            Log.d(TAG, "Attempting to initialize EncryptedSharedPreferences...")

            val initTime = measureTimeMillis {
                encryptedPrefs = createEncryptedPreferences(context)
            }

            Log.d(TAG, "EncryptedSharedPreferences initialized in ${initTime}ms")
            encrypted = true

        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize EncryptedSharedPreferences, falling back to regular SharedPreferences", e)
            encryptedPrefs = null
            encrypted = false
        }

        // Use encrypted prefs if available, otherwise fallback
        if (encryptedPrefs != null) {
            prefs = encryptedPrefs
            isEncrypted = true
            Log.i(TAG, "Using EncryptedSharedPreferences")
        } else {
            prefs = context.getSharedPreferences(PREFS_NAME_FALLBACK, Context.MODE_PRIVATE)
            isEncrypted = false
            Log.w(TAG, "Using regular SharedPreferences as fallback")
        }

        // Store which type we're using for debugging
        try {
            prefs.edit().putBoolean(KEY_USING_ENCRYPTED, isEncrypted).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store encryption status", e)
        }
    }

    private fun createEncryptedPreferences(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Safe getter wrapper
    private fun <T> safeGet(key: String, defaultValue: T, getter: () -> T): T {
        return try {
            getter()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get value for key: $key, using default", e)
            defaultValue
        }
    }

    // Safe setter wrapper
    private fun safeSet(key: String, setter: (SharedPreferences.Editor) -> SharedPreferences.Editor) {
        try {
            val editor = prefs.edit()
            setter(editor).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set value for key: $key", e)
        }
    }

    // Language/Locale preferences
    fun setLocale(locale: String) {
        safeSet(KEY_LOCALE) { editor ->
            editor.putString(KEY_LOCALE, locale)
        }
    }

    fun getLocale(): String {
        return safeGet(KEY_LOCALE, "en") {
            prefs.getString(KEY_LOCALE, "en") ?: "en"
        }
    }

    // Scanner baud (default Honeywell often 115200)
    fun getScannerBaud(): Int {
        return safeGet(KEY_BAUD_RATE, 115200) {
            prefs.getInt(KEY_BAUD_RATE, 115200)
        }
    }

    fun setScannerBaud(value: Int) {
        safeSet(KEY_BAUD_RATE) { editor ->
            editor.putInt(KEY_BAUD_RATE, value)
        }
    }

    // First launch tracking
    fun isFirstLaunch(): Boolean {
        return safeGet(KEY_FIRST_LAUNCH, true) {
            prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        }
    }

    fun setFirstLaunchCompleted() {
        safeSet(KEY_FIRST_LAUNCH) { editor ->
            editor.putBoolean(KEY_FIRST_LAUNCH, false)
        }
    }

    // Onboarding completion
    fun isOnboardingCompleted(): Boolean {
        return safeGet(KEY_ONBOARDING_COMPLETED, false) {
            prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        }
    }

    fun setOnboardingCompleted() {
        safeSet(KEY_ONBOARDING_COMPLETED) { editor ->
            editor.putBoolean(KEY_ONBOARDING_COMPLETED, true)
        }
    }

    // Authentication data
    fun saveAuthData(accessToken: String, refreshToken: String, userType: String, mobileNumber: String, userName: String?) {
        try {
            val editor = prefs.edit()
            editor.putString(KEY_ACCESS_TOKEN, accessToken)
            editor.putString(KEY_REFRESH_TOKEN, refreshToken)
            editor.putString(KEY_USER_TYPE, userType)
            editor.putString(KEY_USER_MOBILE, mobileNumber)
            editor.putString(KEY_USER_NAME, userName)
            editor.putBoolean(KEY_IS_LOGGED_IN, true)
            editor.putLong(KEY_LAST_LOGIN, System.currentTimeMillis())
            editor.apply()

            Log.d(TAG, "Auth data saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save auth data", e)
        }
    }

    fun getAccessToken(): String? {
        return safeGet(KEY_ACCESS_TOKEN, null) {
            prefs.getString(KEY_ACCESS_TOKEN, null)
        }
    }

    fun getRefreshToken(): String? {
        return safeGet(KEY_REFRESH_TOKEN, null) {
            prefs.getString(KEY_REFRESH_TOKEN, null)
        }
    }

    fun getUserType(): String? {
        return safeGet(KEY_USER_TYPE, null) {
            prefs.getString(KEY_USER_TYPE, null)
        }
    }

    fun getUserMobile(): String? {
        return safeGet(KEY_USER_MOBILE, null) {
            prefs.getString(KEY_USER_MOBILE, null)
        }
    }

    fun getUserName(): String? {
        return safeGet(KEY_USER_NAME, null) {
            prefs.getString(KEY_USER_NAME, null)
        }
    }

    fun isLoggedIn(): Boolean {
        return safeGet(KEY_IS_LOGGED_IN, false) {
            prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        }
    }

    fun getLastLogin(): Long {
        return safeGet(KEY_LAST_LOGIN, 0L) {
            prefs.getLong(KEY_LAST_LOGIN, 0L)
        }
    }

    fun clearAuthData() {
        try {
            val editor = prefs.edit()
            editor.remove(KEY_ACCESS_TOKEN)
            editor.remove(KEY_REFRESH_TOKEN)
            editor.remove(KEY_USER_TYPE)
            editor.remove(KEY_USER_MOBILE)
            editor.remove(KEY_USER_NAME)
            editor.putBoolean(KEY_IS_LOGGED_IN, false)
            editor.remove(KEY_LAST_LOGIN)
            editor.apply()

            Log.d(TAG, "Auth data cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear auth data", e)
        }
    }

    fun clearAll() {
        try {
            prefs.edit().clear().apply()
            Log.d(TAG, "All preferences cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all preferences", e)
        }
    }

    // Debug information
    fun getPrefsInfo(): String {
        return "Encryption: ${if (isEncrypted) "Enabled" else "Disabled (Fallback)"}, " +
                "Keys: ${try { prefs.all.size } catch (e: Exception) { "Unknown" }}"
    }
}