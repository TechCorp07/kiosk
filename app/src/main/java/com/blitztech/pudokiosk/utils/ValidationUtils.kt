package com.blitztech.pudokiosk.utils

object ValidationUtils {

    /**
     * Validates a Zimbabwe phone number in ANY of:
     *   +263774613020, 263774613020, 0774613020, 774613020
     * The validation happens AFTER normalization, so callers
     * can pass any of those formats.
     */
    fun isValidPhoneNumber(phone: String): Boolean {
        val normalized = formatPhoneNumber(phone)
        // +263 (3) + 9 digits = 13 chars total
        val phoneRegex = Regex("^\\+2637[0-9]{8}$")
        return phoneRegex.matches(normalized)
    }

    fun isValidPin(pin: String): Boolean {
        // PIN should be 6 digits
        return pin.matches(Regex("^[0-9]{6}$"))
    }

    fun isValidOtp(otp: String): Boolean {
        // OTP should be 6 digits
        return otp.matches(Regex("^[0-9]{6}$"))
    }

    fun isValidEmail(email: String): Boolean {
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        return emailRegex.matches(email)
    }

    /**
     * Validates a Zimbabwe National ID in ANY of:
     *   29-279774-Q-13   (standard)
     *   29-279774-Q13    (missing last separator)
     *   29-279774Q13     (letter glued)
     *   29279774Q13      (no separators at all)
     *   29-279774q13     (lowercase letter)
     *
     * Format:  DD-XXXXXX-L-DD
     *   DD      = 2-digit district (first 2 digits)
     *   XXXXXX  = 5-9 digit sequence number
     *   L       = 1 letter (check letter)
     *   DD      = 2-digit suffix
     *
     * Validation happens AFTER normalization.
     */
    fun isValidNationalId(nationalId: String): Boolean {
        val normalized = formatNationalId(nationalId)
        // Standard format: DD-DDDDD~DDDDDDDDD-L-DD  (5 to 9 digits in middle)
        val nationalIdRegex = Regex("^[0-9]{2}-[0-9]{5,9}-[A-Z]-[0-9]{2}$")
        return nationalIdRegex.matches(normalized)
    }

    /**
     * Normalizes a phone number to +263XXXXXXXXX format.
     *
     * Accepts:  +263774613020, 263774613020, 0774613020, 774613020
     * Returns:  +263774613020
     */
    fun formatPhoneNumber(phone: String): String {
        // Remove spaces, dashes, parentheses — keep + and digits
        val cleaned = phone.replace(Regex("[^+0-9]"), "")

        // Already in international format
        if (cleaned.startsWith("+263")) {
            return cleaned
        }

        // Starts with 07XX (local format) → +2637XX
        if (cleaned.startsWith("07") && cleaned.length == 10) {
            return "+263${cleaned.substring(1)}"
        }

        // Starts with 263 (no +) → +263
        if (cleaned.startsWith("263") && cleaned.length == 12) {
            return "+$cleaned"
        }

        // Starts with 7 and is 9 digits → +2637XXXXXXXX
        if (cleaned.startsWith("7") && cleaned.length == 9) {
            return "+263$cleaned"
        }

        // Return as-is (will fail validation, which is correct)
        return cleaned
    }

    /**
     * Normalizes a national ID to the standard format: DD-XXXXXX-L-DD
     *
     * Accepts:  29-279774-Q-13, 29-279774-Q13, 29-279774Q13,
     *           29279774Q13, 29-279774q13
     * Returns:  29-279774-Q-13
     */
    fun formatNationalId(nationalId: String): String {
        // Uppercase and strip all dashes/spaces
        val raw = nationalId.uppercase().replace(Regex("[-\\s]"), "")

        // Pattern: 2 digits + 5-to-9 digits + 1 letter + 2 digits
        val regex = Regex("^([0-9]{2})([0-9]{5,9})([A-Z])([0-9]{2})$")
        val match = regex.matchEntire(raw) ?: return nationalId.uppercase()

        val (district, sequence, letter, suffix) = match.destructured
        return "$district-$sequence-$letter-$suffix"
    }

    // ── Error messages (user-friendly, show accepted formats) ──────────

    fun getEmailErrorMessage(): String =
        "Please enter a valid email address (e.g., user@example.com)"

    fun getPhoneErrorMessage(): String =
        "Enter a valid number: +263774XXXXXX, 0774XXXXXX, or 774XXXXXX"

    fun getNationalIdErrorMessage(): String =
        "Enter a valid National ID (e.g. 29-279774-Q-13 or 29279774Q13)"
}