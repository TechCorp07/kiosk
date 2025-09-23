package com.blitztech.pudokiosk.data.api.config

object ApiEndpoints {
    // Authentication endpoints
    const val AUTH_LOGIN = "auth/pin"
    const val AUTH_OTP_VERIFY = "auth/otp"
    const val FORGOT_PIN = "users/forgot-pin"
    const val CHANGE_PIN = "users/change-pin"

    // User endpoints
    const val USER_REGISTER = "users"
    const val USER_KYC_UPLOAD = "users/{mobileNumber}/documents"

    // Helper method to build KYC upload URL
    fun getUserKycUrl(mobileNumber: String): String {
        return USER_KYC_UPLOAD.replace("{mobileNumber}", mobileNumber)
    }
}