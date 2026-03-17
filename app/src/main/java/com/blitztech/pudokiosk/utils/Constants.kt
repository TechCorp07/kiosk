package com.blitztech.pudokiosk.utils

import android.content.Context
import android.widget.Toast
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * App-wide constants
 */
object Constants {

    // App Configuration
    const val APP_NAME = "ZIMPUDO Kiosk"
    const val APP_VERSION = "1.0.0"
    const val SUPPORT_EMAIL = "support@zimpudo.com"
    const val SUPPORT_PHONE = "+263-XXX-XXXX-XX"
    const val WEBSITE = "www.zimpudo.com"

    // API Configuration
    const val API_TIMEOUT_SECONDS = 30L
    const val MAX_RETRY_ATTEMPTS = 3
    const val RETRY_DELAY_MS = 1000L

    // Order Constants
    const val MIN_PACKAGE_DIMENSION = 0.01 // meters
    const val MAX_PACKAGE_DIMENSION = 2.0  // meters
    const val MAX_CONTENT_LENGTH = 200     // characters

    // Payment Constants
    const val MIN_PAYMENT_AMOUNT = 1.0
    const val MAX_PAYMENT_AMOUNT = 10000.0

    // Locker Constants
    const val MIN_LOCKER_NUMBER = 1
    const val MAX_LOCKER_NUMBER = 16
    const val LOCKER_UNLOCK_TIMEOUT_MS = 5000L

    // Printer Constants
    const val PRINTER_RECONNECT_ATTEMPTS = 3
    const val PRINTER_RETRY_DELAY_MS = 2000L
    const val RECEIPT_WIDTH_CHARS = 32

    // Location Constants
    const val DEFAULT_LATITUDE = -17.8252  // Harare, Zimbabwe
    const val DEFAULT_LONGITUDE = 31.0335
    const val LOCATION_TIMEOUT_MS = 10000L

    // UI Constants
    const val ANIMATION_DURATION_MS = 300L
    const val DEBOUNCE_DELAY_MS = 500L
    const val TOAST_DURATION_LONG = 3500

    // Date/Time Formats
    const val DATE_FORMAT = "yyyy-MM-dd"
    const val TIME_FORMAT = "HH:mm:ss"
    const val DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss"
    const val DISPLAY_DATE_FORMAT = "dd MMM yyyy"
    const val DISPLAY_DATETIME_FORMAT = "dd MMM yyyy, HH:mm"

    // Validation Patterns
    const val PHONE_PATTERN = "^\\+2637[0-9]{8}$"
    const val NATIONAL_ID_PATTERN = "^[0-9]{2}-[0-9]{6,7}-[A-Z]-[0-9]{2}$"
    const val EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"

    // Storage Keys
    const val PREF_ACCESS_TOKEN = "access_token"
    const val PREF_USER_NAME = "user_name"
    const val PREF_USER_MOBILE = "user_mobile"
    const val PREF_USER_ID = "user_id"
    const val PREF_DEVICE_ID = "device_id"
    const val PREF_LOCKER_ID = "locker_id"

    // Request/Result Codes
    const val REQUEST_CODE_LOCATION = 1001
    const val REQUEST_CODE_STORAGE = 1002
    const val RESULT_CODE_SUCCESS = 2001
    const val RESULT_CODE_FAILURE = 2002
}

/**
 * Extension functions for common operations
 */

// String extensions
fun String.isValidPhone(): Boolean = this.matches(Regex(Constants.PHONE_PATTERN))
fun String.isValidNationalId(): Boolean = this.matches(Regex(Constants.NATIONAL_ID_PATTERN))
fun String.isValidEmail(): Boolean = this.matches(Regex(Constants.EMAIL_PATTERN))

fun String.formatPhoneNumber(): String {
    // Format +2637XXXXXXXX to +263 7X XXX XXXX
    if (this.length == 13 && this.startsWith("+263")) {
        return "${substring(0, 4)} ${substring(4, 6)} ${substring(6, 9)} ${substring(9)}"
    }
    return this
}

fun String.maskPhoneNumber(): String {
    // Mask middle digits: +2637XXXXXXXX -> +263 7X XXX XXXX -> +263 7X *** XXXX
    if (this.length >= 13) {
        return "${substring(0, 6)}***${substring(9)}"
    }
    return this
}

fun String.truncate(maxLength: Int, suffix: String = "..."): String {
    return if (this.length <= maxLength) this else substring(0, maxLength - suffix.length) + suffix
}

// Double extensions
fun Double.formatCurrency(currencyCode: String): String {
    val symbol = when (currencyCode) {
        "USD" -> "$"
        "ZWL" -> "Z$"
        else -> currencyCode
    }
    return "$symbol${String.format("%.2f", this)}"
}

fun Double.formatDimension(): String = String.format("%.2f", this)

fun Double.toMeters(): String = "${String.format("%.2f", this)}m"

// Date extensions
fun Date.formatDate(pattern: String = Constants.DATETIME_FORMAT): String {
    return SimpleDateFormat(pattern, Locale.getDefault()).format(this)
}

fun Long.toDate(): Date = Date(this)

fun String.parseDate(pattern: String = Constants.DATETIME_FORMAT): Date? {
    return try {
        SimpleDateFormat(pattern, Locale.getDefault()).parse(this)
    } catch (e: Exception) {
        null
    }
}

// Context extensions
fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Context.showLongToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

/**
 * Helper functions for formatting
 */
object FormatHelper {

    /**
     * Format file size
     */
    fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        return when {
            gb >= 1 -> String.format("%.2f GB", gb)
            mb >= 1 -> String.format("%.2f MB", mb)
            kb >= 1 -> String.format("%.2f KB", kb)
            else -> "$bytes B"
        }
    }

    /**
     * Format duration in milliseconds to human-readable
     */
    fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            hours > 0 -> String.format("%dh %dm", hours, minutes % 60)
            minutes > 0 -> String.format("%dm %ds", minutes, seconds % 60)
            else -> String.format("%ds", seconds)
        }
    }

    /**
     * Format distance in kilometers
     */
    fun formatDistance(km: Double): String {
        return when {
            km < 1 -> String.format("%.0f m", km * 1000)
            km < 10 -> String.format("%.1f km", km)
            else -> String.format("%.0f km", km)
        }
    }

    /**
     * Format percentage
     */
    fun formatPercentage(value: Double): String {
        return String.format("%.1f%%", value * 100)
    }

    /**
     * Format number with thousands separator
     */
    fun formatNumber(number: Long): String {
        return NumberFormat.getInstance(Locale.getDefault()).format(number)
    }

    /**
     * Format order ID for display
     */
    fun formatOrderId(orderId: String): String {
        // If UUID format, show shortened version
        if (orderId.contains("-") && orderId.length > 20) {
            return orderId.substring(0, 8).uppercase()
        }
        return orderId.uppercase()
    }
}

/**
 * Error message formatter
 */
object ErrorMessageHelper {

    fun getNetworkErrorMessage(throwable: Throwable): String {
        return when {
            throwable.message?.contains("timeout", ignoreCase = true) == true ->
                "Connection timeout. Please check your internet connection."

            throwable.message?.contains("unable to resolve host", ignoreCase = true) == true ->
                "Cannot reach server. Please check your internet connection."

            throwable.message?.contains("connection refused", ignoreCase = true) == true ->
                "Server is not responding. Please try again later."

            else -> "Network error. Please try again."
        }
    }

    fun getApiErrorMessage(code: Int): String {
        return when (code) {
            400 -> "Invalid request. Please check your input."
            401 -> "Session expired. Please login again."
            403 -> "Access denied. You don't have permission for this action."
            404 -> "Resource not found. Please contact support."
            409 -> "Conflict. This action cannot be completed."
            500 -> "Server error. Please try again later."
            502 -> "Server is temporarily unavailable."
            503 -> "Service unavailable. Please try again later."
            else -> "An error occurred (Code: $code). Please try again."
        }
    }

    fun getHardwareErrorMessage(device: String, error: String): String {
        return "Hardware Error - $device: $error\n\nPlease contact technical support if the problem persists."
    }
}

/**
 * Input validators
 */
object InputValidator {

    fun isValidDimension(value: Double): Boolean {
        return value >= Constants.MIN_PACKAGE_DIMENSION && value <= Constants.MAX_PACKAGE_DIMENSION
    }

    fun isValidAmount(value: Double): Boolean {
        return value >= Constants.MIN_PAYMENT_AMOUNT && value <= Constants.MAX_PAYMENT_AMOUNT
    }

    fun isValidLockerNumber(number: Int): Boolean {
        return number in Constants.MIN_LOCKER_NUMBER..Constants.MAX_LOCKER_NUMBER
    }

    fun isValidContent(content: String): Boolean {
        return content.isNotBlank() && content.length <= Constants.MAX_CONTENT_LENGTH
    }

    fun sanitizeInput(input: String): String {
        // Remove potentially dangerous characters
        return input.replace(Regex("[<>\"'\\\\]"), "")
    }
}

/**
 * Retry helper for operations
 */
class RetryHelper(
    private val maxAttempts: Int = Constants.MAX_RETRY_ATTEMPTS,
    private val delayMs: Long = Constants.RETRY_DELAY_MS
) {
    suspend fun <T> retry(operation: suspend () -> T): T {
        var lastException: Exception? = null

        repeat(maxAttempts) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxAttempts - 1) {
                    kotlinx.coroutines.delay(delayMs * (attempt + 1))
                }
            }
        }

        throw lastException ?: Exception("Operation failed after $maxAttempts attempts")
    }
}

/**
 * Device information helper
 */
object DeviceInfoHelper {

    fun getDeviceInfo(): String {
        return buildString {
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("App: ${Constants.APP_NAME} v${Constants.APP_VERSION}")
        }
    }

    fun getDeviceId(context: Context): String {
        // Generate or retrieve unique device ID
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString(Constants.PREF_DEVICE_ID, null)

        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(Constants.PREF_DEVICE_ID, deviceId).apply()
        }

        return deviceId
    }
}