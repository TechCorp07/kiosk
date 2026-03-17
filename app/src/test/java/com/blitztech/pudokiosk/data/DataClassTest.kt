package com.blitztech.pudokiosk.data

import com.blitztech.pudokiosk.data.api.dto.auth.*
import com.blitztech.pudokiosk.data.api.dto.collection.*
import com.blitztech.pudokiosk.data.api.dto.common.*
import com.blitztech.pudokiosk.data.api.dto.courier.*
import com.blitztech.pudokiosk.data.api.dto.customer.CustomerParcel
import com.blitztech.pudokiosk.data.api.dto.location.CityDto
import com.blitztech.pudokiosk.data.api.dto.location.SuburbDto
import com.blitztech.pudokiosk.data.api.dto.order.*
import com.blitztech.pudokiosk.data.api.dto.user.Address
import com.blitztech.pudokiosk.data.api.dto.user.SignUpRequest
import com.blitztech.pudokiosk.data.db.*
import com.blitztech.pudokiosk.data.net.CourierLoginRequest
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests data class instantiation, equality, copy, and default values
 * for all DTOs and Room entities in the project.
 */
class DataClassTest {

    // ──── Auth DTOs ──────────────────────────────────────────────
    @Test
    fun loginRequest_defaultOtpMethod() {
        val req = LoginRequest("123", "1234")
        assertEquals("SMS_EMAIL", req.otpMethod)
    }

    @Test
    fun loginRequest_equality() {
        val a = LoginRequest("123", "1234")
        val b = LoginRequest("123", "1234")
        assertEquals(a, b)
    }

    @Test
    fun loginResponse_nullableTokens() {
        val resp = LoginResponse(null, null, "PENDING_OTP")
        assertNull(resp.accessToken)
        assertNull(resp.refreshToken)
        assertEquals("PENDING_OTP", resp.status)
    }

    @Test
    fun otpVerifyRequest_fields() {
        val req = OtpVerifyRequest("123", "4567")
        assertEquals("123", req.mobileNumber)
        assertEquals("4567", req.otp)
    }

    @Test
    fun otpVerifyResponse_fields() {
        val resp = OtpVerifyResponse("token", "refresh", "AUTHENTICATED")
        assertEquals("token", resp.accessToken)
        assertEquals("refresh", resp.refreshToken)
    }

    @Test
    fun pinChangeRequest_fields() {
        val req = PinChangeRequest("old", "new")
        assertEquals("old", req.oldPin)
        assertEquals("new", req.newPin)
    }

    @Test
    fun forgotPinRequest_defaultOtpMethod() {
        val req = ForgotPinRequest("123")
        assertEquals("SMS_EMAIL", req.otpMethod)
    }

    // ──── Collection DTOs ────────────────────────────────────────
    @Test
    fun recipientAuthRequest_fields() {
        val req = RecipientAuthRequest("CODE123", "KIOSK-1")
        assertEquals("CODE123", req.collectionCode)
        assertEquals("KIOSK-1", req.kioskId)
    }

    @Test
    fun recipientAuthResponse_fields() {
        val resp = RecipientAuthResponse(true, "OK", "o1", "L1", 5, "John", "M")
        assertTrue(resp.success)
        assertEquals(5, resp.cellNumber)
        assertEquals("John", resp.recipientName)
    }

    @Test
    fun lockerOpenRequest_fields() {
        val req = LockerOpenRequest("L1", 3)
        assertEquals("L1", req.lockerId)
        assertEquals(3, req.cellNumber)
    }

    @Test
    fun lockerPickupRequest_fields() {
        val req = LockerPickupRequest("o1", "KIOSK-1")
        assertEquals("o1", req.orderId)
        assertEquals("KIOSK-1", req.kioskId)
    }

    // ──── Courier DTOs ───────────────────────────────────────
    @Test
    fun transactionRequest_fields() {
        val req = TransactionRequest("TRK123", "KIOSK-1", "L1", 5)
        assertEquals("TRK123", req.trackingNumber)
        assertEquals("KIOSK-1", req.kioskId)
        assertEquals("L1", req.lockerId)
        assertEquals(5, req.cellNumber)
    }

    @Test
    fun transactionRequest_optionalFieldsNull() {
        val req = TransactionRequest("TRK123", "KIOSK-1")
        assertNull(req.lockerId)
        assertNull(req.cellNumber)
    }

    @Test
    fun transactionResponse_fields() {
        val resp = TransactionResponse(true, "OK", "t1", "o1", "L1", 5, "TRK", "John", "COMPLETED")
        assertTrue(resp.success)
        assertEquals(5, resp.cellNumber)
        assertEquals("John", resp.recipientName)
    }

    @Test
    fun courierParcel_fields() {
        val p = com.blitztech.pudokiosk.data.api.dto.courier.CourierParcel(
            "p1", "o1", 5, "TRK", "M", "John", "IN_TRANSIT"
        )
        assertEquals(5, p.lockNumber)
    }

    @Test
    fun parcelLookupRequest_fields() {
        val req = ParcelLookupRequest("barcode", "kiosk")
        assertEquals("barcode", req.barcode)
    }

    @Test
    fun orderDto_nullableFields() {
        val order = OrderDto(id = "o1", trackingNumber = "TRK001", status = "PENDING")
        assertEquals("o1", order.id)
        assertEquals("TRK001", order.trackingNumber)
        assertNull(order.senderName)
        assertNull(order.cellNumber)
    }

    @Test
    fun pageOrder_defaults() {
        val page = PageOrder()
        assertTrue(page.content.isEmpty())
        assertEquals(0, page.totalElements)
        assertTrue(page.first)
        assertTrue(page.last)
    }

    // ──── Customer DTOs ──────────────────────────────────────────
    @Test
    fun customerParcel_nullableLockNumber() {
        val p = CustomerParcel("p1", "TRK", "DELIVERED", null, "M", null, "2024-01-01")
        assertNull(p.lockNumber)
        assertNull(p.senderName)
    }

    // ──── Location DTOs ──────────────────────────────────────────
    @Test
    fun cityDto_fields() {
        val c = CityDto("c1", "Harare")
        assertEquals("Harare", c.name)
    }

    @Test
    fun suburbDto_fields() {
        val city = CityDto("c1", "Harare")
        val suburb = SuburbDto("s1", "Avondale", city)
        assertEquals("Avondale", suburb.name)
        assertEquals("Harare", suburb.city.name)
    }

    // ──── Order DTOs ─────────────────────────────────────────────
    @Test
    fun packageDimensions_fields() {
        val d = PackageDimensions(0.5, 0.3, 0.2)
        assertEquals(0.5, d.length, 0.001)
    }

    @Test
    fun recipientAddress_defaultCountry() {
        val addr = RecipientAddress("123 Main", "Avondale", "Harare", "42")
        assertEquals("Zimbabwe", addr.country)
    }

    @Test
    fun paymentRequest_fields() {
        val req = PaymentRequest("o1", "l1", "PAYNOW", "123", "USD")
        assertEquals("PAYNOW", req.paymentMethod)
    }

    @Test
    fun paymentResponse_nullableFields() {
        val resp = PaymentResponse(true, "OK", null, null)
        assertNull(resp.transactionId)
        assertNull(resp.lockNumber)
    }

    // ──── User DTOs ──────────────────────────────────────────────
    @Test
    fun address_fields() {
        val addr = Address("c1", "s1", "Main St", "42")
        assertEquals("c1", addr.city)
    }

    @Test
    fun signUpRequest_defaultRole() {
        val addr = Address("c1", "s1", "Main", "1")
        val req = SignUpRequest("John", "Doe", "j@e.com", "123", "NID1", addr)
        assertEquals("USER", req.role)
    }

    @Test
    fun apiResponse_defaultNullErrors() {
        val resp = ApiResponse(true, "OK")
        assertNull(resp.errors)
    }

    // ──── Room Entities ──────────────────────────────────────────
    @Test
    fun auditLogEntity_autoGenerateId() {
        val e = AuditLogEntity(ts = 100L, level = "INFO", event = "test", details = null)
        assertEquals(0L, e.id) // auto-generate default
    }

    @Test
    fun configEntity_defaultKey() {
        val e = ConfigEntity(version = 1L, json = "{}", updatedAt = 100L)
        assertEquals("device", e.key)
    }

    @Test
    fun lockerEntity_fields() {
        val e = LockerEntity("L1", "M", true, null, null)
        assertTrue(e.isClosed)
        assertNull(e.lastOpenAt)
    }

    @Test
    fun outboxEventEntity_defaultDelivered() {
        val e = OutboxEventEntity("key1", "TYPE", "{}", 100L)
        assertFalse(e.delivered)
    }

    @Test
    fun parcelEntity_defaults() {
        val e = ParcelEntity("p1", "s1", "r1", "M", "CREATED", null, "TRK", 100L)
        assertEquals(0, e.lockNumber)
        assertNull(e.collectionCode)
        assertEquals(0L, e.updatedAt)
    }

    @Test
    fun securityPhotoEntity_defaults() {
        val e = SecurityPhotoEntity(
            filePath = "/photo.jpg",
            reason = "CLIENT_DEPOSIT",
            referenceId = "o1",
            userId = "123",
            kioskId = "K1",
            capturedAt = 100L
        )
        assertEquals(0L, e.id)
        assertFalse(e.uploaded)
    }

    @Test
    fun userEntity_fields() {
        val e = UserEntity("u1", "123", "a@b.com", "USER", "VERIFIED", true, 100L)
        assertTrue(e.pinSet)
        assertEquals("VERIFIED", e.kycStatus)
    }

    // ──── data/net DTOs ──────────────────────────────────────────
    @Test
    fun courierLoginRequest_net() {
        val req = CourierLoginRequest("1234")
        assertEquals("1234", req.codeOrPin)
    }

    @Test
    fun courierParcel_net() {
        val p = com.blitztech.pudokiosk.data.net.CourierParcel("p1", "L1", "TRK", "M")
        assertEquals("p1", p.parcelId)
    }

    // ──── Copy / equality ────────────────────────────────────────
    @Test
    fun dataClass_copy() {
        val e = ParcelEntity("p1", "s1", "r1", "M", "CREATED", null, "TRK", 100L)
        val updated = e.copy(status = "IN_LOCKER", lockNumber = 5)
        assertEquals("IN_LOCKER", updated.status)
        assertEquals(5, updated.lockNumber)
        assertEquals(e.id, updated.id)
    }
}
