package com.blitztech.pudokiosk.data.api

import com.blitztech.pudokiosk.data.api.config.ApiEndpoints
import com.blitztech.pudokiosk.data.api.dto.auth.*
import com.blitztech.pudokiosk.data.api.dto.user.*
import com.blitztech.pudokiosk.data.api.dto.common.*
import com.blitztech.pudokiosk.data.api.dto.location.*
import com.blitztech.pudokiosk.data.api.dto.order.*
import com.blitztech.pudokiosk.data.api.dto.collection.*
import com.blitztech.pudokiosk.data.api.dto.courier.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit service interface for the Zimpudo backend.
 * Endpoints match the backend OpenAPI specifications.
 */
interface ApiService {
    // ─────────────────────────────────────────────────────────────
    //  Auth Service – 2-factor authentication (PIN + OTP)
    // ─────────────────────────────────────────────────────────────
    @POST(ApiEndpoints.AUTH_PIN)
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST(ApiEndpoints.AUTH_OTP)
    suspend fun verifyOtp(@Body request: OtpVerifyRequest): Response<OtpVerifyResponse>

    @POST(ApiEndpoints.AUTH_BACKUP_CODE)
    suspend fun verifyBackupCode(@Body request: BackupCodeVerifyRequest): Response<LoginResponse>

    // ─────────────────────────────────────────────────────────────
    //  Core Service – PIN management
    // ─────────────────────────────────────────────────────────────
    @GET(ApiEndpoints.FORGOT_PIN)
    suspend fun forgotPin(
        @Query("mobileNumber") mobileNumber: String,
        @Query("otpMethod") otpMethod: String
    ): Response<ApiResponse>

    @POST(ApiEndpoints.CHANGE_PIN)
    suspend fun changePin(
        @Body request: PinChangeRequest,
        @Header("Authorization") token: String
    ): Response<ApiResponse>

    // ─────────────────────────────────────────────────────────────
    //  Core Service – User registration & KYC
    // ─────────────────────────────────────────────────────────────
    @POST(ApiEndpoints.USER_REGISTER)
    suspend fun registerUser(@Body request: SignUpRequest): Response<RegistrationResponse>

    @Multipart
    @POST(ApiEndpoints.USER_KYC_UPLOAD)
    suspend fun uploadKyc(
        @Path("mobileNumber") mobileNumber: String,
        @Part type: MultipartBody.Part,
        @Part file: MultipartBody.Part
    ): Response<KycResponse>

    // ─────────────────────────────────────────────────────────────
    //  Order Service – Orders & payments
    // ─────────────────────────────────────────────────────────────
    @POST(ApiEndpoints.CREATE_ORDER)
    suspend fun createOrder(
        @Body request: CreateOrderRequest,
        @Header("Authorization") token: String
    ): Response<CreateOrderResponse>

    @POST(ApiEndpoints.CREATE_PAYMENT)
    suspend fun createPayment(
        @Body request: PaymentRequest,
        @Header("Authorization") token: String
    ): Response<PaymentResponse>

    // ─────────────────────────────────────────────────────────────
    //  Order Service – Customer order tracking
    // ─────────────────────────────────────────────────────────────
    @GET(ApiEndpoints.ORDERS_LOGGED_IN)
    suspend fun getLoggedInOrders(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): Response<PageOrder>

    @GET(ApiEndpoints.TRACK_ORDER)
    suspend fun trackOrder(
        @Path("trackingNumber") trackingNumber: String
    ): Response<OrderDto>

    // ─────────────────────────────────────────────────────────────
    //  Order Service – Locations
    // ─────────────────────────────────────────────────────────────
    @GET(ApiEndpoints.GET_CITIES)
    suspend fun getCities(): Response<List<CityDto>>

    @GET(ApiEndpoints.GET_SUBURBS)
    suspend fun getSuburbs(@Path("cityId") cityId: String): Response<List<SuburbDto>>

    // ─────────────────────────────────────────────────────────────
    //  Order Service – Locker operations (recipient collection)
    // ─────────────────────────────────────────────────────────────
    @POST(ApiEndpoints.RECIPIENT_AUTH)
    suspend fun authenticateRecipient(
        @Body request: RecipientAuthRequest
    ): Response<RecipientAuthResponse>

    @POST(ApiEndpoints.LOCKER_PICKUP)
    suspend fun completePickup(
        @Body request: LockerPickupRequest,
        @Header("Authorization") token: String
    ): Response<ApiResponse>

    @POST(ApiEndpoints.LOCKER_OPEN_CELL)
    suspend fun openCell(
        @Body request: LockerOpenRequest,
        @Header("Authorization") token: String
    ): Response<ApiResponse>

    // ─────────────────────────────────────────────────────────────
    //  Locker Service – Courier transactions
    //  Couriers authenticate via the same Auth Service (AUTH_PIN + AUTH_OTP)
    // ─────────────────────────────────────────────────────────────
    @POST(ApiEndpoints.COURIER_PICKUP)
    suspend fun courierPickup(
        @Body request: TransactionRequest,
        @Header("Authorization") token: String
    ): Response<TransactionResponse>

    @POST(ApiEndpoints.COURIER_DROPOFF)
    suspend fun courierDropoff(
        @Body request: TransactionRequest,
        @Header("Authorization") token: String
    ): Response<TransactionResponse>

    // ─────────────────────────────────────────────────────────────
    //  Locker Service – Sender transactions
    // ─────────────────────────────────────────────────────────────
    @POST(ApiEndpoints.VERIFY_RESERVATION)
    suspend fun verifyReservation(
        @Body request: TransactionRequest,
        @Header("Authorization") token: String
    ): Response<TransactionResponse>

    @POST(ApiEndpoints.SENDER_DROPOFF)
    suspend fun senderDropoff(
        @Body request: TransactionRequest,
        @Header("Authorization") token: String
    ): Response<TransactionResponse>

    // ─────────────────────────────────────────────────────────────
    //  Security photo upload
    // ─────────────────────────────────────────────────────────────
    @Multipart
    @POST(ApiEndpoints.UPLOAD_SECURITY_PHOTO)
    suspend fun uploadSecurityPhoto(
        @Part photo: okhttp3.MultipartBody.Part,
        @Part("reason") reason: okhttp3.RequestBody,
        @Part("referenceId") referenceId: okhttp3.RequestBody,
        @Part("userId") userId: okhttp3.RequestBody,
        @Part("kioskId") kioskId: okhttp3.RequestBody,
        @Part("capturedAt") capturedAt: okhttp3.RequestBody
    ): Response<ApiResponse>
}