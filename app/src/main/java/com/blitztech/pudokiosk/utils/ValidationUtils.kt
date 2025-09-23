package com.blitztech.pudokiosk.utils

object ValidationUtils {

    fun isValidPhoneNumber(phone: String): Boolean {
        // Zimbabwe phone number validation (+263 followed by 9 digits)
        val phoneRegex = Regex("^\\+2637[0-9]{8}$")
        return phoneRegex.matches(phone)
    }

    fun isValidPin(pin: String): Boolean {
        // PIN should be 4-6 digits
        return pin.matches(Regex("^[0-9]{4,6}$"))
    }

    fun isValidOtp(otp: String): Boolean {
        // OTP should be 6 digits
        return otp.matches(Regex("^[0-9]{6}$"))
    }

    fun isValidEmail(email: String): Boolean {
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        return emailRegex.matches(email)
    }

    fun isValidNationalId(nationalId: String): Boolean {
        // Zimbabwe National ID format: XX-XXXXXXX A XX
        val nationalIdRegex = Regex("^[0-9]{2}-[0-9]{7} [A-Z] [0-9]{2}$")
        return nationalIdRegex.matches(nationalId)
    }

    fun formatPhoneNumber(phone: String): String {
        // Remove any non-digit characters except +
        val cleaned = phone.replace(Regex("[^+0-9]"), "")

        // If starts with 07, replace with +2637
        if (cleaned.startsWith("07")) {
            return "+263${cleaned.substring(1)}"
        }

        // If starts with 263, add +
        if (cleaned.startsWith("263")) {
            return "+$cleaned"
        }

        // If starts with 7 and is 9 digits, add +263
        if (cleaned.startsWith("7") && cleaned.length == 9) {
            return "+263$cleaned"
        }

        return cleaned
    }
}