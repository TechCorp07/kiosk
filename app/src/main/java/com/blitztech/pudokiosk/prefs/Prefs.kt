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
        private const val KEY_PRIMARY_LOCKER_UUID = "primary_locker_uuid"
        private const val KEY_KIOSK_DEVICE_ID = "kiosk_device_id"
        private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
        private const val KEY_PROVISIONED = "kiosk_provisioned"
        private const val KEY_SITE_NAME = "kiosk_site_name"
        private const val KEY_LOCKER_COUNT = "kiosk_locker_count"
        private const val KEY_ALL_LOCKER_UUIDS = "all_locker_uuids"
        private const val KEY_API_BASE_URL_OVERRIDE = "api_base_url_override"
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

    fun setOnboardingCompleted(bool: Boolean) {
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

    fun saveUserMobile(mobile: String) {
        prefs.edit().putString(KEY_USER_MOBILE, mobile).apply()
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

    fun putFloat(key: String, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }

    fun getFloat(key: String, defaultValue: Float = 0f): Float {
        return prefs.getFloat(key, defaultValue)
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

    // Security camera settings
    fun isCameraEnabled(): Boolean = getBoolean("camera_enabled", true)
    fun setCameraEnabled(enabled: Boolean) = putBoolean("camera_enabled", enabled)

    fun getCameraJpegQuality(): Int = getInt("camera_jpeg_quality", 85)
    fun setCameraJpegQuality(quality: Int) = putInt("camera_jpeg_quality", quality.coerceIn(30, 100))

    fun getPhotoRetentionDays(): Int = getInt("photo_retention_days", 30)
    fun setPhotoRetentionDays(days: Int) = putInt("photo_retention_days", days.coerceIn(1, 365))

    // Maintenance mode (technician unlocks device for servicing)
    fun isMaintenanceMode(): Boolean = getBoolean("maintenance_mode", false)
    fun setMaintenanceMode(enabled: Boolean) = putBoolean("maintenance_mode", enabled)

    // ── Developer Options ──────────────────────────────────────────────────
    /**
     * Set to TRUE to bypass RS485 and RS232 hardware requirements for Emulator testing.
     * To turn it off, set the defaultValue to false.
     */
    fun isHardwareBypassEnabled(): Boolean = getBoolean("hardware_bypass_enabled", false)
    fun setHardwareBypassEnabled(enabled: Boolean) = putBoolean("hardware_bypass_enabled", enabled)

    // OTA update settings
    fun getUpdateServerUrl(): String = getString("update_server_url", "https://api.zimpudo.com")
    fun setUpdateServerUrl(url: String) = putString("update_server_url", url)

    // ── Locker provisioning ──────────────────────────────────────────────
    /**
     * The backend UUID of this kiosk's primary locker.
     * Set during provisioning via the admin screen.
     * Used for: cell assignment (courier dropoff), heartbeat, pending-collections sync.
     */
    fun getPrimaryLockerUuid(): String = getString(KEY_PRIMARY_LOCKER_UUID, "")
    fun savePrimaryLockerUuid(uuid: String) = putString(KEY_PRIMARY_LOCKER_UUID, uuid)

    /**
     * All locker UUIDs assigned to this kiosk (comma-separated).
     * Set during provisioning. Used by LockerSyncWorker to sync
     * cell inventory for ALL lockers, not just the primary.
     */
    fun getAllLockerUuids(): List<String> {
        val raw = getString(KEY_ALL_LOCKER_UUIDS, "")
        return if (raw.isBlank()) {
            // Backward-compat: fall back to primary-only if not set
            val primary = getPrimaryLockerUuid()
            if (primary.isNotBlank()) listOf(primary) else emptyList()
        } else {
            raw.split(",").filter { it.isNotBlank() }
        }
    }
    fun saveAllLockerUuids(uuids: List<String>) =
        putString(KEY_ALL_LOCKER_UUIDS, uuids.joinToString(","))

    /**
     * Unique device ID for this kiosk (generated once, stored permanently).
     * Used for CLIENT_CREDENTIALS device-level JWT authentication.
     */
    fun getKioskDeviceId(): String {
        var id = getString(KEY_KIOSK_DEVICE_ID, "")
        if (id.isBlank()) {
            id = java.util.UUID.randomUUID().toString()
            putString(KEY_KIOSK_DEVICE_ID, id)
        }
        return id
    }

    /**
     * Token expiry timestamp (epoch ms). Enables proactive token refresh
     * without parsing the JWT on every request.
     */
    fun getTokenExpiresAt(): Long = getLong(KEY_TOKEN_EXPIRES_AT, 0L)
    fun setTokenExpiresAt(epochMs: Long) = putLong(KEY_TOKEN_EXPIRES_AT, epochMs)

    /** Returns true if the stored token has expired or expires within the next 5 minutes. */
    fun isTokenExpiredOrExpiringSoon(): Boolean {
        val expiresAt = getTokenExpiresAt()
        if (expiresAt == 0L) return true // Never set — assume expired
        val nowPlusBuffer = System.currentTimeMillis() + (5 * 60 * 1000L) // 5 min buffer
        return nowPlusBuffer >= expiresAt
    }

    // ── Kiosk Provisioning ───────────────────────────────────────────────

    /**
     * True once a technician has completed the 1-time provisioning wizard
     * (locker UUID, site name, network config). Until provisioned, the kiosk
     * shows the provisioning screen instead of the customer/courier UI.
     */
    fun isProvisioned(): Boolean = getBoolean(KEY_PROVISIONED, false)
    fun setProvisioned(done: Boolean) = putBoolean(KEY_PROVISIONED, done)

    /** Human-readable name of the site (e.g. "Westgate Shopping Centre"). */
    fun getSiteName(): String = getString(KEY_SITE_NAME, "Zimpudo Kiosk")
    fun setSiteName(name: String) = putString(KEY_SITE_NAME, name)

    /** Number of physical locker units attached to this kiosk (1..4). */
    fun getLockerCount(): Int = getInt(KEY_LOCKER_COUNT, 1)
    fun setLockerCount(count: Int) = putInt(KEY_LOCKER_COUNT, count.coerceIn(1, 4))

    /**
     * Optional API base URL override for staging/dev kiosks.
     * Blank = use default https://api.zimpudo.com.
     */
    fun getApiBaseUrlOverride(): String = getString(KEY_API_BASE_URL_OVERRIDE, "")
    fun setApiBaseUrlOverride(url: String) = putString(KEY_API_BASE_URL_OVERRIDE, url)

    // Kiosk Location (Provisioned)
    fun getKioskLatitude(): Double = java.lang.Double.longBitsToDouble(
        prefs.getLong("kiosk_latitude", java.lang.Double.doubleToRawLongBits(-17.8252))
    )
    fun setKioskLatitude(lat: Double) = putLong("kiosk_latitude", java.lang.Double.doubleToRawLongBits(lat))

    fun getKioskLongitude(): Double = java.lang.Double.longBitsToDouble(
        prefs.getLong("kiosk_longitude", java.lang.Double.doubleToRawLongBits(31.0335))
    )
    fun setKioskLongitude(lng: Double) = putLong("kiosk_longitude", java.lang.Double.doubleToRawLongBits(lng))
}