package com.blitztech.pudokiosk.data.api

import com.blitztech.pudokiosk.data.api.config.ApiEndpoints
import com.blitztech.pudokiosk.data.api.dto.auth.*
import com.blitztech.pudokiosk.data.api.dto.user.*
import com.blitztech.pudokiosk.data.api.dto.common.*
import com.blitztech.pudokiosk.data.api.dto.location.*
import com.blitztech.pudokiosk.data.api.dto.order.*
import com.blitztech.pudokiosk.data.api.dto.collection.*
import com.blitztech.pudokiosk.data.api.dto.courier.*
import com.blitztech.pudokiosk.data.api.dto.locker.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
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
    suspend fun registerUser(
        @Body request: SignUpRequest,
        @Header("Authorization") token: String
    ): Response<RegistrationResponse>

    /** Walk-in kiosk registration — POST /api/v1/users/partial (no auth required) */
    @POST(ApiEndpoints.USER_PARTIAL_REGISTER)
    suspend fun partialRegisterUser(
        @Body request: SignUpRequest
    ): Response<RegistrationResponse>

    @Multipart
    @POST(ApiEndpoints.USER_KYC_UPLOAD)
    suspend fun uploadKyc(
        @Path("mobileNumber") mobileNumber: String,
        @Part type: MultipartBody.Part,
        @Part file: MultipartBody.Part
    ): Response<KycResponse>

    @GET(ApiEndpoints.USER_PROFILE)
    suspend fun getUserProfile(
        @Header("Authorization") token: String
    ): Response<UserProfileDto>

    @PUT(ApiEndpoints.USER_PROFILE)
    suspend fun updateUserProfile(
        @Body request: UserProfileUpdateRequest,
        @Header("Authorization") token: String
    ): Response<com.blitztech.pudokiosk.data.api.dto.common.ApiResponse>

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

    @POST(ApiEndpoints.SEARCH_PAYMENT)
    suspend fun searchPaymentByOrderId(
        @Body request: Map<String, String>,
        @Header("Authorization") token: String
    ): Response<PaymentSearchPage>

    @PATCH("api/v1/orders/{orderId}/cancel")
    suspend fun cancelOrder(
        @Path("orderId") orderId: String,
        @Header("Authorization") token: String
    ): Response<PaymentResponse>  // Backend returns GenericResponse { success, message }

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
        @Path("trackingNumber") trackingNumber: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50
    ): Response<com.blitztech.pudokiosk.data.api.dto.order.PageOrderTrackerDto>

    // ─────────────────────────────────────────────────────────────
    //  Order Service – Locations
    // ─────────────────────────────────────────────────────────────
    @GET(ApiEndpoints.GET_CITIES)
    suspend fun getCities(): Response<PageCity>

    @GET(ApiEndpoints.GET_SUBURBS)
    suspend fun getSuburbs(@Path("cityId") cityId: String): Response<PageSuburb>

    @GET(ApiEndpoints.PACKAGE_CONTENT_TYPES)
    suspend fun getPackageContentTypes(): Response<PageContents>

    // ─────────────────────────────────────────────────────────────
    //  Order Service – Locker operations (recipient collection)
    //  Both endpoints are PUBLIC (no Authorization header needed).
    // ─────────────────────────────────────────────────────────────
    @POST(ApiEndpoints.RECIPIENT_AUTH)
    suspend fun authenticateRecipient(
        @Body request: RecipientAuthRequest,
        @Header("Authorization") token: String
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
    //  Orders Service – Courier kiosk operations
    //  Note: uses Orders Service (not Locker Service) per backend architecture.
    // ─────────────────────────────────────────────────────────────

    /** Search order by trackingNumber — resolves orderId for subsequent calls. */
    @POST(ApiEndpoints.ORDER_SEARCH)
    suspend fun searchOrder(
        @Body request: Map<String, String>,
        @Header("Authorization") token: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): Response<OrderSearchPage>

    /**
     * Courier barcode scan — marks parcel as PICKED_UP from the kiosk locker.
     * POST /api/v1/orders/{orderId}/pickup-scan?barcode=...
     */
    @POST
    suspend fun courierPickupScan(
        @Url url: String,
        @Query("barcode") barcode: String,
        @Header("Authorization") token: String
    ): Response<CourierOpsResponse>

    /**
     * Courier drops parcel at destination PUDO locker.
     * POST /api/v1/transactions/courier/dropoff
     */
    @Multipart
    @POST(ApiEndpoints.COURIER_DROPOFF_LOCKER)
    suspend fun courierDropoffAtLocker(
        @Part("orderId") orderId: RequestBody,
        @Part("waybillNumber") waybillNumber: RequestBody,
        @Part("destinationLockerId") destinationLockerId: RequestBody,
        @Part("cellId") cellId: RequestBody,
        @Part photos: List<MultipartBody.Part>,
        @Header("Authorization") token: String
    ): Response<com.blitztech.pudokiosk.data.api.dto.common.ApiResponse>

    /**
     * View today's route (orders assigned to this courier).
     * GET /api/v1/orders/couriers
     */
    @GET(ApiEndpoints.COURIER_ORDERS)
    suspend fun getCourierOrders(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50
    ): Response<PageOrder>

    /**
     * Report an issue with a parcel at the kiosk.
     * POST /api/v1/orders/{orderId}/issue
     */
    @POST
    suspend fun reportCourierIssue(
        @Url url: String,
        @Body request: Map<String, Any>,
        @Header("Authorization") token: String
    ): Response<ApiResponse>

    /**
     * Accept/bind an order to this courier.
     * PATCH /api/v1/orders/{orderId}/bind
     */
    @PATCH
    suspend fun courierBindOrder(
        @Url url: String,
        @Header("Authorization") token: String
    ): Response<ApiResponse>

    // ─────────────────────────────────────────────────────────────
    //  Locker Service – Sender transactions
    // ─────────────────────────────────────────────────────────────
    @POST(ApiEndpoints.VERIFY_RESERVATION)
    suspend fun verifyReservation(
        @Body request: VerifyReservationRequest,
        @Header("Authorization") token: String
    ): Response<TransactionApiResponse>

    /**
     * Sender drop-off — multipart/form-data matching backend SenderDropOffRequest:
     *   orderId: UUID (required)
     *   cellId: UUID (required)
     *   photos: List<MultipartFile> (nullable per schema)
     */
    @Multipart
    @POST(ApiEndpoints.SENDER_DROPOFF)
    suspend fun senderDropoff(
        @Part("orderId") orderId: RequestBody,
        @Part("cellId") cellId: RequestBody,
        @Part photos: List<MultipartBody.Part>,
        @Header("Authorization") token: String
    ): Response<com.blitztech.pudokiosk.data.api.dto.common.ApiResponse>

    /**
     * Courier pickup at source locker — POST /api/v1/transactions/courier/pickup
     * No request body — courier identity derived from JWT.
     * Returns list of packages assigned to courier at this locker.
     */
    @POST(ApiEndpoints.COURIER_PICKUP_LOCKER)
    suspend fun courierPickupFromLocker(
        @Query("lockerId") lockerId: String,
        @Header("Authorization") token: String
    ): Response<CourierPickupApiResponse>

    // ─────────────────────────────────────────────────────────────
    //  Locker Service – Cell & locker sync (API role / device-level token)
    // ─────────────────────────────────────────────────────────────

    /** GET /api/v1/lockers/{lockerId}/cells — fetch all cells for a locker */
    @GET
    suspend fun getLockerCells(
        @Url url: String,
        @Header("X-API-KEY") apiKey: String,
        @Header("X-API-SERVICE") apiService: String
    ): Response<KioskCellsApiResponse>

    /** PATCH /api/v1/lockers/{lockerId}/status?status=ONLINE — kiosk heartbeat */
    @PATCH
    suspend fun patchLockerStatus(
        @Url url: String,
        @Query("status") status: String,
        @Header("X-API-KEY") apiKey: String,
        @Header("X-API-SERVICE") apiService: String
    ): Response<ApiResponse>

    /** PATCH /api/v1/cells/{cellId}/status — report cell jam / maintenance */
    @PATCH
    suspend fun patchCellStatus(
        @Url url: String,
        @Body request: Map<String, String>,
        @Header("Authorization") token: String
    ): Response<ApiResponse>

    /** GET /api/v1/lockers/nearest/multiple — browse destination lockers */
    @GET(ApiEndpoints.NEAREST_LOCKERS)
    suspend fun getNearestLockers(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("limit") limit: Int = 5,
        @Header("Authorization") token: String
    ): Response<NearestLockersApiResponse>

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

    // ─────────────────────────────────────────────────────────────
    //  Kiosk Provisioning (no auth required — uses daily OTP)
    // ─────────────────────────────────────────────────────────────
    @POST(ApiEndpoints.KIOSK_PROVISION)
    suspend fun provisionKiosk(
        @Body request: com.blitztech.pudokiosk.data.api.dto.kiosk.KioskProvisionRequest
    ): Response<com.blitztech.pudokiosk.data.api.dto.kiosk.KioskProvisionApiResponse>
}