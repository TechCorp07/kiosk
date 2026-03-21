package com.blitztech.pudokiosk.data.api.config

/**
 * API endpoint paths matching the Zimpudo backend OpenAPI specifications.
 *
 * Auth Service    → https://api.zimpudo.com  (port 8085 local)
 * Core Service    → https://api.zimpudo.com  (port 4000 local)
 * Locker Service  → https://api.zimpudo.com  (port 8084 local)
 * Order Service   → https://api.zimpudo.com  (port 8081 local)
 * Payment Service → https://api.zimpudo.com  (port 8082 local)
 */
object ApiEndpoints {

    // ── Auth Service ────────────────────────────────────────────
    const val AUTH_PIN = "api/v1/auth/pin"
    const val AUTH_OTP = "api/v1/auth/otp"
    const val AUTH_BACKUP_CODE = "api/v1/auth/backup-code"

    // ── Core Service – Authentication ───────────────────────────
    const val FORGOT_PIN = "api/v1/users/forgot-pin"         // GET with ?mobileNumber=&otpMethod=
    const val CHANGE_PIN = "api/v1/users/change-pin"

    // ── Core Service – User Registration ────────────────────────
    const val USER_REGISTER = "api/v1/users"
    const val USER_KYC_UPLOAD = "api/v1/users/{mobileNumber}/documents"
    const val USER_PROFILE = "api/v1/users/profile"

    // ── Order Service ───────────────────────────────────────────
    const val CREATE_ORDER = "api/v1/orders"
    const val ORDERS_LOGGED_IN = "api/v1/orders/logged-in"
    const val TRACK_ORDER = "api/v1/orders/track/{trackingNumber}"

    // ── Order Service – Location ────────────────────────────────
    const val GET_CITIES = "api/v1/orders/cities"
    const val GET_SUBURBS = "api/v1/orders/cities/{cityId}/suburbs"

    // ── Payment Service ─────────────────────────────────────────
    const val CREATE_PAYMENT = "api/v1/payments"
    const val PAYMENT_STATUS = "api/v1/payments/status/{transactionId}"

    // ── Locker Service – Transactions ───────────────────────────
    const val VERIFY_RESERVATION = "api/v1/transactions/sender/verify-reservation"
    const val SENDER_DROPOFF = "api/v1/transactions/sender/dropoff"
    const val COURIER_PICKUP = "api/v1/transactions/courier/pickup"
    const val COURIER_DROPOFF = "api/v1/transactions/courier/dropoff"

    // ── Locker Service – Management ─────────────────────────────
    const val NEAREST_LOCKERS = "api/v1/lockers/nearest/multiple"
    const val NEAREST_LOCKERS_WITH_SIZE = "api/v1/lockers/nearest/multiple-with-size"
    const val LOCKERS_WITHIN_RADIUS = "api/v1/lockers/within-radius"

    // ── Order Service – Locker Operations ───────────────────────
    const val RECIPIENT_AUTH = "api/v1/locker/recipient/auth"
    const val LOCKER_PICKUP = "api/v1/locker/pickup"
    const val LOCKER_OPEN_CELL = "api/v1/locker/open"

    // ── Security Photos (kiosk-specific) ────────────────────────
    const val UPLOAD_SECURITY_PHOTO = "api/v1/kiosks/security-photos"


    fun getUserKycUrl(mobileNumber: String): String =
        USER_KYC_UPLOAD.replace("{mobileNumber}", mobileNumber)

    fun getTrackOrderUrl(trackingNumber: String): String =
        TRACK_ORDER.replace("{trackingNumber}", trackingNumber)

    fun getPaymentStatusUrl(transactionId: String): String =
        PAYMENT_STATUS.replace("{transactionId}", transactionId)

    fun getSuburbsUrl(cityId: String): String =
        GET_SUBURBS.replace("{cityId}", cityId)
}