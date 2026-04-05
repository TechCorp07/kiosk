package com.blitztech.pudokiosk.data.api.config

/**
 * Global API configuration for the Zimpudo kiosk application.
 *
 * The BASE_URL points to the API gateway which routes to individual services:
 *   Auth    (8085), Core (4000), Locker (8084), Order (8081), Payment (8082)
 */
object ApiConfig {
    // Gateway base URL - Dynamically overridden by NetworkModule based on DeveloperMode settings
    const val BASE_URL = "https://api.zimpudo.com/"

    // Request timeout configurations (seconds)
    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT = 30L
    const val WRITE_TIMEOUT = 30L

    // Fixed values for API requests
    const val OTP_METHOD = "SMS_EMAIL"        // or "TOTP"
    const val USER_ROLE = "USER"
    const val KYC_TYPE = "NATIONAL_ID"       // also: PASSPORT, DRIVERS_LICENSE

    // Phone number configuration
    const val PHONE_COUNTRY_CODE = "+263"
    const val PHONE_PLACEHOLDER = "+2637XXXXXXXX"

    // Headers
    const val CONTENT_TYPE_JSON = "application/json"
    const val CONTENT_TYPE_MULTIPART = "multipart/form-data"
}