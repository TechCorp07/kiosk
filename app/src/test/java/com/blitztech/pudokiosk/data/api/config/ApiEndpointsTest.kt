package com.blitztech.pudokiosk.data.api.config

import org.junit.Assert.*
import org.junit.Test

class ApiEndpointsTest {

    // ── Auth Service endpoints ───────────────────────────────────
    @Test
    fun authEndpoints_haveApiV1Prefix() {
        assertTrue(ApiEndpoints.AUTH_PIN.startsWith("api/v1/"))
        assertTrue(ApiEndpoints.AUTH_OTP.startsWith("api/v1/"))
        assertTrue(ApiEndpoints.AUTH_BACKUP_CODE.startsWith("api/v1/"))
    }

    @Test
    fun authPin_correctPath() {
        assertEquals("api/v1/auth/pin", ApiEndpoints.AUTH_PIN)
    }

    @Test
    fun authOtp_correctPath() {
        assertEquals("api/v1/auth/otp", ApiEndpoints.AUTH_OTP)
    }

    @Test
    fun authBackupCode_correctPath() {
        assertEquals("api/v1/auth/backup-code", ApiEndpoints.AUTH_BACKUP_CODE)
    }

    // ── Core Service endpoints ───────────────────────────────────
    @Test
    fun forgotPin_correctPath() {
        assertEquals("api/v1/users/forgot-pin", ApiEndpoints.FORGOT_PIN)
    }

    @Test
    fun changePin_correctPath() {
        assertEquals("api/v1/users/change-pin", ApiEndpoints.CHANGE_PIN)
    }

    @Test
    fun userRegister_correctPath() {
        assertEquals("api/v1/users", ApiEndpoints.USER_REGISTER)
    }

    @Test
    fun userKycUpload_containsPlaceholder() {
        assertTrue(ApiEndpoints.USER_KYC_UPLOAD.contains("{mobileNumber}"))
        assertTrue(ApiEndpoints.USER_KYC_UPLOAD.startsWith("api/v1/"))
    }

    @Test
    fun userProfile_correctPath() {
        assertEquals("api/v1/users/profile", ApiEndpoints.USER_PROFILE)
    }

    // ── Order Service endpoints ──────────────────────────────────
    @Test
    fun createOrder_correctPath() {
        assertEquals("api/v1/orders", ApiEndpoints.CREATE_ORDER)
    }

    @Test
    fun getCities_correctPath() {
        assertEquals("api/v1/orders/cities", ApiEndpoints.GET_CITIES)
    }

    @Test
    fun getSuburbs_containsCityIdPlaceholder() {
        assertTrue(ApiEndpoints.GET_SUBURBS.contains("{cityId}"))
        assertEquals("api/v1/orders/cities/{cityId}/suburbs", ApiEndpoints.GET_SUBURBS)
    }

    // ── Payment Service endpoints ────────────────────────────────
    @Test
    fun createPayment_correctPath() {
        assertEquals("api/v1/payments", ApiEndpoints.CREATE_PAYMENT)
    }

    @Test
    fun paymentStatus_containsTransactionIdPlaceholder() {
        assertTrue(ApiEndpoints.PAYMENT_STATUS.contains("{transactionId}"))
    }

    // ── Locker Transaction endpoints ─────────────────────────────
    @Test
    fun lockerTransactionEndpoints_haveApiV1Prefix() {
        assertTrue(ApiEndpoints.VERIFY_RESERVATION.startsWith("api/v1/"))
        assertTrue(ApiEndpoints.SENDER_DROPOFF.startsWith("api/v1/"))
        assertTrue(ApiEndpoints.COURIER_PICKUP.startsWith("api/v1/"))
        assertTrue(ApiEndpoints.COURIER_DROPOFF.startsWith("api/v1/"))
    }

    @Test
    fun lockerOperationEndpoints_correctPaths() {
        assertEquals("api/v1/locker/recipient/auth", ApiEndpoints.RECIPIENT_AUTH)
        assertEquals("api/v1/locker/pickup", ApiEndpoints.LOCKER_PICKUP)
        assertEquals("api/v1/locker/open", ApiEndpoints.LOCKER_OPEN_CELL)
    }

    // ── Nearest Lockers ──────────────────────────────────────────
    @Test
    fun nearestLockers_correctPaths() {
        assertEquals("api/v1/lockers/nearest/multiple", ApiEndpoints.NEAREST_LOCKERS)
        assertEquals("api/v1/lockers/nearest/multiple-with-size", ApiEndpoints.NEAREST_LOCKERS_WITH_SIZE)
        assertEquals("api/v1/lockers/within-radius", ApiEndpoints.LOCKERS_WITHIN_RADIUS)
    }

    // ── Security photos ──────────────────────────────────────────
    @Test
    fun securityPhoto_correctPath() {
        assertEquals("api/v1/kiosks/security-photos", ApiEndpoints.UPLOAD_SECURITY_PHOTO)
    }

    // ── URL builder functions ────────────────────────────────────
    @Test
    fun getUserKycUrl_replacesPlaceholder() {
        val url = ApiEndpoints.getUserKycUrl("+263771234567")
        assertEquals("api/v1/users/+263771234567/documents", url)
        assertFalse(url.contains("{mobileNumber}"))
    }

    @Test
    fun getTrackOrderUrl_replacesPlaceholder() {
        val url = ApiEndpoints.getTrackOrderUrl("TRK-12345")
        assertEquals("api/v1/orders/track/TRK-12345", url)
        assertFalse(url.contains("{trackingNumber}"))
    }

    @Test
    fun getPaymentStatusUrl_replacesPlaceholder() {
        val url = ApiEndpoints.getPaymentStatusUrl("tx-abc")
        assertEquals("api/v1/payments/status/tx-abc", url)
        assertFalse(url.contains("{transactionId}"))
    }

    @Test
    fun getSuburbsUrl_replacesPlaceholder() {
        val url = ApiEndpoints.getSuburbsUrl("city-123")
        assertEquals("api/v1/orders/cities/city-123/suburbs", url)
    }
}
