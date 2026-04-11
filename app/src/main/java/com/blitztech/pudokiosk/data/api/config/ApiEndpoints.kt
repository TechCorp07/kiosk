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

    // ── Global Fleet Scope ──────────────────────────────────────
    const val KIOSK_API_KEY = "zimpudo-kiosk-live-key"
    const val KIOSK_API_SERVICE = "kiosk-fleet"

    // ── Auth Service ────────────────────────────────────────────
    const val AUTH_PIN = "api/v1/auth/pin"
    const val AUTH_OTP = "api/v1/auth/otp"
    const val AUTH_BACKUP_CODE = "api/v1/auth/backup-code"

    // ── Core Service – Authentication ───────────────────────────
    const val FORGOT_PIN = "api/v1/users/forgot-pin"         // GET with ?mobileNumber=&otpMethod=
    const val CHANGE_PIN = "api/v1/users/change-pin"

    // ── Core Service – User Registration ────────────────────────
    const val USER_REGISTER = "api/v1/users"
    const val USER_PARTIAL_REGISTER = "api/v1/users/partial"   // No auth — walk-in kiosk registration
    const val USER_KYC_UPLOAD = "api/v1/users/{mobileNumber}/documents"
    const val USER_PROFILE = "api/v1/users/profile"

    // ── Order Service ───────────────────────────────────────────
    const val CREATE_ORDER = "api/v1/orders"
    const val ORDERS_LOGGED_IN = "api/v1/orders/logged-in"
    const val CANCEL_ORDER = "api/v1/orders/{orderId}/cancel"
    const val PACKAGE_CONTENT_TYPES = "api/v1/orders/packages"
    const val TRACK_ORDER = "api/v1/orders/track/{trackingNumber}"

    // ── Order Service – Location ────────────────────────────────
    const val GET_CITIES = "api/v1/orders/cities"
    const val GET_SUBURBS = "api/v1/orders/cities/{cityId}/suburbs"

    // ── Payment Service ─────────────────────────────────────────
    const val CREATE_PAYMENT = "api/v1/payments"
    const val SEARCH_PAYMENT = "api/v1/payments/or-search"

    // ── Locker Service – Transactions ───────────────────────────
    const val VERIFY_RESERVATION = "api/v1/transactions/sender/verify-reservation"
    const val SENDER_DROPOFF = "api/v1/transactions/sender/dropoff"
    // Locker-service courier endpoints
    const val COURIER_PICKUP_LOCKER = "api/v1/transactions/courier/pickup"     // No body — courier identity from JWT
    const val COURIER_DROPOFF_LOCKER = "api/v1/transactions/courier/dropoff"

    // ── Orders Service – Courier kiosk operations ────────────────
    // POST /api/v1/orders/{orderId}/pickup-scan?barcode=...   → marks PICKED_UP
    const val COURIER_PICKUP_SCAN = "api/v1/orders/{orderId}/pickup-scan"
    // POST /api/v1/orders/and-search  { trackingNumber: "..." } → resolve orderId from barcode
    const val ORDER_SEARCH = "api/v1/orders/and-search"
    // GET /api/v1/orders/couriers?page=0&size=50 → View routes assigned to courier
    const val COURIER_ORDERS = "api/v1/orders/couriers"
    // POST /api/v1/orders/{orderId}/issue → Report issue with a parcel
    const val COURIER_ISSUE = "api/v1/orders/{orderId}/issue"

    // ── Locker Cell Management (kiosk heartbeat + status reporting) ─
    const val LOCKER_STATUS = "api/v1/lockers/{lockerId}/status"   // PATCH ?status=ONLINE (API role)
    const val LOCKER_CELLS = "api/v1/lockers/{lockerId}/cells"      // GET — sync cell inventory
    const val CELL_STATUS = "api/v1/cells/{cellId}/status"          // PATCH — report field status

    // ── Order Service – Locker Operations ───────────────────────
    const val RECIPIENT_AUTH = "api/v1/locker/recipient/auth"       // public
    const val LOCKER_PICKUP = "api/v1/locker/pickup"                // public
    const val LOCKER_OPEN_CELL = "api/v1/locker/open"              // authenticated

    // ── Security Photos (kiosk-specific) ────────────────────────
    const val UPLOAD_SECURITY_PHOTO = "api/v1/kiosks/security-photos"
    // TODO [POST-LAUNCH]: Add POST /api/v1/kiosks/security-photos on backend

    // ── Locker Service – Discovery ──────────────────────────────
    const val NEAREST_LOCKERS = "api/v1/lockers/nearest/multiple"
    const val NEAREST_LOCKERS_WITH_SIZE = "api/v1/lockers/nearest/multiple-with-size"
    const val LOCKERS_WITHIN_RADIUS = "api/v1/lockers/within-radius"

    // ── Pending Collections (offline sync) ──────────────────────
    // TODO [P0 BACKEND]: GET /api/v1/orders/pending-collections?lockerId= (orders in AWAITING_COLLECTION with hashed OTPs)
    // Uncomment once backend endpoint is available:
    // const val PENDING_COLLECTIONS = "api/v1/orders/pending-collections"

    fun getCourierPickupScanUrl(orderId: String): String =
        COURIER_PICKUP_SCAN.replace("{orderId}", orderId)

    fun getCourierIssueUrl(orderId: String): String =
        COURIER_ISSUE.replace("{orderId}", orderId)

    fun getLockerStatusUrl(lockerId: String): String =
        LOCKER_STATUS.replace("{lockerId}", lockerId)

    fun getLockerCellsUrl(lockerId: String): String =
        LOCKER_CELLS.replace("{lockerId}", lockerId)

    fun getCellStatusUrl(cellId: String): String =
        CELL_STATUS.replace("{cellId}", cellId)

    fun getUserKycUrl(mobileNumber: String): String =
        USER_KYC_UPLOAD.replace("{mobileNumber}", mobileNumber)

    fun getTrackOrderUrl(trackingNumber: String): String =
        TRACK_ORDER.replace("{trackingNumber}", trackingNumber)

    fun getSuburbsUrl(cityId: String): String =
        GET_SUBURBS.replace("{cityId}", cityId)

    fun getCancelOrderUrl(orderId: String): String =
        CANCEL_ORDER.replace("{orderId}", orderId)

    // ── Kiosk provisioning ─────────────────────────────────────────
    const val KIOSK_PROVISION = "api/v1/kiosks/provision"
}