package com.blitztech.pudokiosk.data.api.config

object ApiConfig {
    // Global API Configuration
    const val BASE_URL = "http://68.183.176.201:8222/api/v1/"

    // Request timeout configurations
    const val CONNECT_TIMEOUT = 30L // seconds
    const val READ_TIMEOUT = 30L // seconds
    const val WRITE_TIMEOUT = 30L // seconds

    // Fixed values for API requests
    const val OTP_METHOD = "SMS_EMAIL"
    const val USER_ROLE = "USER"
    const val KYC_TYPE = "NATIONAL_ID"

    // Phone number configuration
    const val PHONE_COUNTRY_CODE = "+263"
    const val PHONE_PLACEHOLDER = "+2637XXXXXXXX"

    // Headers
    const val CONTENT_TYPE_JSON = "application/json"
    const val CONTENT_TYPE_MULTIPART = "multipart/form-data"
}