package com.blitztech.pudokiosk.update

/**
 * Configuration constants for the OTA remote update system.
 * Change UPDATE_BASE_URL to point to your actual update server.
 */
object UpdateConfig {

    /**
     * Base URL of the update server.
     * The endpoint should return JSON:
     * {
     *   "versionCode": 2,
     *   "versionName": "0.2.0",
     *   "apkUrl": "https://your-server.com/releases/pudokiosk-0.2.0.apk",
     *   "releaseNotes": "Bug fixes and improvements",
     *   "mandatory": false
     * }
     */
    const val UPDATE_BASE_URL = "https://api.zimpudo.com"

    /** Endpoint path to check for latest version. */
    const val UPDATE_CHECK_PATH = "/api/kiosk/update/latest"

    /** Full URL for update check. */
    val UPDATE_CHECK_URL: String get() = "$UPDATE_BASE_URL$UPDATE_CHECK_PATH"

    /** How often to check for updates (in hours). */
    const val CHECK_INTERVAL_HOURS = 6L

    /** Minimum interval between checks (in hours) to avoid duplicate checks. */
    const val FLEX_INTERVAL_HOURS = 1L

    /** Unique tag for the periodic work request. */
    const val UPDATE_WORK_TAG = "pudokiosk_update_check"

    /** Download buffer size. */
    const val DOWNLOAD_BUFFER_SIZE = 8192

    /** Connection timeout for update checks (seconds). */
    const val CONNECT_TIMEOUT_SECONDS = 30L

    /** Read timeout for APK download (seconds). */
    const val READ_TIMEOUT_SECONDS = 300L
}
