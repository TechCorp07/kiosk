package com.blitztech.pudokiosk.data.repository

import android.content.Context
import android.util.Log
import com.blitztech.pudokiosk.data.api.ApiService
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.config.ApiConfig
import com.blitztech.pudokiosk.data.api.config.ApiEndpoints
import com.blitztech.pudokiosk.data.api.dto.auth.*
import com.blitztech.pudokiosk.data.api.dto.user.*
import com.blitztech.pudokiosk.data.api.dto.common.*
import com.blitztech.pudokiosk.data.api.dto.locker.*
import com.blitztech.pudokiosk.data.api.dto.courier.*
import com.blitztech.pudokiosk.data.api.dto.location.CityDto
import com.blitztech.pudokiosk.data.api.dto.location.SuburbDto
import com.blitztech.pudokiosk.data.api.dto.order.CreateOrderRequest
import com.blitztech.pudokiosk.data.api.dto.order.CreateOrderResponse
import com.blitztech.pudokiosk.data.api.dto.order.PackageDetails
import com.blitztech.pudokiosk.data.api.dto.order.PaymentRequest
import com.blitztech.pudokiosk.data.api.dto.order.PaymentResponse
import com.blitztech.pudokiosk.data.api.dto.order.PaymentSearchPage
import com.blitztech.pudokiosk.data.api.dto.order.Recipient
import com.blitztech.pudokiosk.data.api.dto.order.SenderLocation
import com.blitztech.pudokiosk.data.api.dto.order.OrderDto
import com.blitztech.pudokiosk.data.api.dto.order.PageOrder
import com.blitztech.pudokiosk.data.api.dto.collection.*
import com.blitztech.pudokiosk.data.api.dto.courier.TransactionRequest
import com.blitztech.pudokiosk.data.api.dto.courier.TransactionResponse
import com.blitztech.pudokiosk.data.api.dto.courier.VerifyReservationBody
import com.blitztech.pudokiosk.data.api.dto.courier.CourierOpsResponse
import com.blitztech.pudokiosk.data.api.dto.courier.OrderSearchPage
import com.blitztech.pudokiosk.data.api.dto.locker.CellDto
import com.blitztech.pudokiosk.data.api.dto.order.VerifyReservationRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import java.io.IOException

class ApiRepository(
    private val apiService: ApiService,
    private val context: Context
) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    companion object {
        private const val TAG = "ApiRepository"
        private const val MAX_RETRY_ATTEMPTS = 7
        private const val RETRY_DELAY_MS = 1000L
    }

    // ─────────────────────────────────────────────────────────────
    //  Auth Service – PIN + OTP (same for users and couriers)
    // ─────────────────────────────────────────────────────────────
    suspend fun login(mobileNumber: String, pin: String): NetworkResult<LoginResponse> {
        return safeApiCall {
            val request = LoginRequest(
                mobileNumber = mobileNumber,
                pin = pin,
                otpMethod = ApiConfig.OTP_METHOD
            )
            apiService.login(request)
        }
    }

    suspend fun verifyOtp(mobileNumber: String, otp: String): NetworkResult<OtpVerifyResponse> {
        return safeApiCall {
            val request = OtpVerifyRequest(mobileNumber, otp)
            apiService.verifyOtp(request)
        }
    }

    suspend fun verifyBackupCode(mobileNumber: String, backupCode: String): NetworkResult<LoginResponse> {
        return safeApiCall {
            val request = BackupCodeVerifyRequest(mobileNumber, backupCode)
            apiService.verifyBackupCode(request)
        }
    }

    suspend fun forgotPin(mobileNumber: String): NetworkResult<ApiResponse> {
        return safeApiCall {
            apiService.forgotPin(mobileNumber, ApiConfig.OTP_METHOD)
        }
    }

    suspend fun changePin(oldPin: String, newPin: String, token: String): NetworkResult<ApiResponse> {
        return safeApiCall {
            val request = PinChangeRequest(oldPin, newPin)
            apiService.changePin(request, "Bearer $token")
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Core Service – User registration & KYC
    // ─────────────────────────────────────────────────────────────
    suspend fun getUserProfile(token: String): NetworkResult<UserProfileDto> {
        return safeApiCall {
            apiService.getUserProfile("Bearer $token")
        }
    }

    suspend fun updateUserProfile(
        request: UserProfileUpdateRequest,
        token: String
    ): NetworkResult<com.blitztech.pudokiosk.data.api.dto.common.ApiResponse> {
        return safeApiCall {
            apiService.updateUserProfile(request, "Bearer $token")
        }
    }

    suspend fun registerUser(
        name: String,
        surname: String,
        email: String,
        mobileNumber: String,
        nationalId: String,
        houseNumber: String,
        street: String,
        suburb: String,
        city: String,
        token: String
    ): NetworkResult<RegistrationResponse> {
        return safeApiCall {
            val address = Address(
                city = city,
                suburb = suburb,
                street = street,
                houseNumber = houseNumber
            )
            val request = SignUpRequest(
                name = name,
                surname = surname,
                email = email,
                mobileNumber = mobileNumber,
                nationalId = nationalId,
                address = address,
                role = ApiConfig.USER_ROLE
            )
            apiService.registerUser(request, "Bearer $token")
        }
    }

    /**
     * Walk-in kiosk partial registration — POST /api/v1/users/partial
     * No auth required. Sets kycStatus = NONE (soft KYC limits).
     */
    suspend fun partialRegisterUser(
        name: String,
        surname: String,
        email: String,
        mobileNumber: String,
        nationalId: String,
        houseNumber: String,
        street: String,
        suburb: String,
        city: String
    ): NetworkResult<RegistrationResponse> {
        return safeApiCall {
            val address = Address(
                city = city,
                suburb = suburb,
                street = street,
                houseNumber = houseNumber
            )
            val request = SignUpRequest(
                name = name,
                surname = surname,
                email = email,
                mobileNumber = mobileNumber,
                nationalId = nationalId,
                address = address,
                role = ApiConfig.USER_ROLE
            )
            apiService.partialRegisterUser(request)
        }
    }

    suspend fun uploadKyc(mobileNumber: String): NetworkResult<KycResponse> {
        return safeApiCall {
            val tempFile = File(context.cacheDir, "kyc_placeholder.pdf")
            try {
                context.assets.open("kyc_placeholder.pdf").use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                val typePart = MultipartBody.Part.createFormData("type", ApiConfig.KYC_TYPE)
                val requestFile = tempFile.asRequestBody("application/pdf".toMediaTypeOrNull())
                val filePart = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)
                apiService.uploadKyc(mobileNumber, typePart, filePart)
            } catch (e: Exception) {
                Log.e("ApiRepository", "Error uploading KYC file", e)
                throw e
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Location methods with retry logic
    // ─────────────────────────────────────────────────────────────
    suspend fun getCities(): NetworkResult<List<CityDto>> {
        return when (val result = safeApiCallWithRetry { apiService.getCities() }) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.content)
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.Loading -> NetworkResult.Loading()
        }
    }

    suspend fun getSuburbs(cityId: String): NetworkResult<List<SuburbDto>> {
        return when (val result = safeApiCallWithRetry { apiService.getSuburbs(cityId) }) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.content)
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.Loading -> NetworkResult.Loading()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Order Service – Orders & payments
    // ─────────────────────────────────────────────────────────────
    suspend fun createOrder(
        packageDetails: PackageDetails,
        recipient: Recipient,
        senderLocation: SenderLocation,
        currency: String,
        token: String?,
        receiverMode: String = "LOCKER_PICKUP"
    ): NetworkResult<CreateOrderResponse> {
        return safeApiCall {
            val request = CreateOrderRequest(
                packageDetails = packageDetails,
                recipient = recipient,
                senderLocation = senderLocation,
                currency = currency,
                receiverMode = receiverMode
            )
            apiService.createOrder(request, "Bearer $token")
        }
    }

    suspend fun createPayment(
        orderId: String,
        lockerId: String,
        paymentMethod: String,
        mobileNumber: String,
        currency: String,
        token: String?
    ): NetworkResult<PaymentResponse> {
        return safeApiCall {
            val request = PaymentRequest(
                orderId = orderId,
                lockerId = lockerId,
                paymentMethod = paymentMethod,
                mobileNumber = mobileNumber,
                currency = currency
            )
            apiService.createPayment(request, "Bearer $token")
        }
    }

    /**
     * Cancel an unpaid order. Backend sets status to CANCELLED.
     */
    suspend fun cancelOrder(orderId: String, token: String?): NetworkResult<PaymentResponse> {
        return safeApiCall {
            apiService.cancelOrder(orderId, "Bearer $token")
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Order Service – Customer order tracking
    // ─────────────────────────────────────────────────────────────
    suspend fun getLoggedInOrders(token: String, page: Int = 0, size: Int = 20): NetworkResult<PageOrder> {
        return safeApiCall { apiService.getLoggedInOrders("Bearer $token", page, size) }
    }

    suspend fun trackOrder(trackingNumber: String): NetworkResult<com.blitztech.pudokiosk.data.api.dto.order.PageOrderTrackerDto> {
        return safeApiCall { apiService.trackOrder(trackingNumber, 0, 50) }
    }

    // ─────────────────────────────────────────────────────────────
    //  Order Service – Locker operations (recipient collection)
    // ─────────────────────────────────────────────────────────────
    suspend fun authenticateRecipient(
        request: RecipientAuthRequest,
        token: String
    ): NetworkResult<RecipientAuthResponse> {
        return safeApiCall { apiService.authenticateRecipient(request, "Bearer $token") }
    }

    suspend fun completePickup(
        request: LockerPickupRequest,
        token: String
    ): NetworkResult<ApiResponse> {
        return safeApiCall { apiService.completePickup(request, "Bearer $token") }
    }

    suspend fun getPackageContentTypes(): NetworkResult<List<String>> {
        return when (val result = safeApiCall { apiService.getPackageContentTypes() }) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.content.map { it.name })
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.Loading -> NetworkResult.Loading()
        }
    }

    suspend fun openCell(
        request: LockerOpenRequest,
        token: String
    ): NetworkResult<ApiResponse> {
        return safeApiCall { apiService.openCell(request, "Bearer $token") }
    }

    // ─────────────────────────────────────────────────────────────
    //  Orders Service – Courier kiosk operations
    // ─────────────────────────────────────────────────────────────

    /**
     * Search order by trackingNumber (barcode scan).
     * Returns orderId needed for subsequent courier operations.
     */
    suspend fun searchOrder(
        barcode: String,
        token: String
    ): NetworkResult<OrderSearchPage> {
        return safeApiCall {
            apiService.searchOrder(
                request = mapOf("trackingNumber" to barcode),
                token = "Bearer $token"
            )
        }
    }

    /**
     * Search order by orderId (UUID).
     * Used by payment polling to check order status after payment initiation.
     */
    suspend fun searchOrderById(
        orderId: String,
        token: String
    ): NetworkResult<OrderSearchPage> {
        return safeApiCall {
            apiService.searchOrder(
                request = mapOf("orderId" to orderId),
                token = "Bearer $token"
            )
        }
    }

    /**
     * Search orders by sender mobile number.
     * Uses POST /orders/and-search with senderMobileNumber.
     * Used by CustomerMainActivity to check for pending drop-off reservations.
     */
    suspend fun searchOrdersBySender(
        senderMobileNumber: String,
        token: String
    ): NetworkResult<OrderSearchPage> {
        return safeApiCall {
            apiService.searchOrder(
                request = mapOf("senderMobileNumber" to senderMobileNumber),
                token = "Bearer $token"
            )
        }
    }

    /**
     * Poll payment status by orderId.
     * Uses POST /api/v1/payments/or-search with orderId.
     * Fallback for payment confirmation polling when the Paynow webhook
     * hasn't yet updated the order status.
     */
    suspend fun searchPaymentByOrderId(
        orderId: String,
        token: String
    ): NetworkResult<PaymentSearchPage> {
        return safeApiCall {
            apiService.searchPaymentByOrderId(
                request = mapOf("orderId" to orderId),
                token = "Bearer $token"
            )
        }
    }

    /**
     * Courier barcode scan at kiosk: marks order as PICKED_UP.
     * POST /api/v1/orders/{orderId}/pickup-scan?barcode=...
     */
    suspend fun courierPickupScan(
        orderId: String,
        barcode: String,
        token: String
    ): NetworkResult<CourierOpsResponse> {
        return safeApiCall {
            val url = ApiEndpoints.getCourierPickupScanUrl(orderId)
            apiService.courierPickupScan(url, barcode, "Bearer $token")
        }
    }

    /**
     * Courier drops parcel at destination PUDO locker.
     * POST /api/v1/transactions/courier/dropoff
     */
    suspend fun courierDropoffAtLocker(
        orderId: String,
        barcode: String,
        destinationLockerId: String,
        cellId: String,
        token: String
    ): NetworkResult<com.blitztech.pudokiosk.data.api.dto.common.ApiResponse> {
        return safeApiCall {
            val orderIdBody = orderId.toRequestBody("text/plain".toMediaType())
            val barcodeBody = barcode.toRequestBody("text/plain".toMediaType())
            val destinationLockerIdBody = destinationLockerId.toRequestBody("text/plain".toMediaType())
            val cellIdBody = cellId.toRequestBody("text/plain".toMediaType())

            val photos = try {
                val bytes = context.assets.open("placeholder_deposit.jpg").readBytes()
                val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData(
                    name = "photos",
                    filename = "placeholder_deposit.jpg",
                    body = requestBody
                )
                listOf(part)
            } catch (e: Exception) {
                Log.w(TAG, "Placeholder photo not found in assets, sending without photo", e)
                emptyList<MultipartBody.Part>()
            }

            apiService.courierDropoffAtLocker(orderIdBody, barcodeBody, destinationLockerIdBody, cellIdBody, photos, "Bearer $token")
        }
    }

    /**
     * Fetch all orders assigned to this courier for today's route.
     * GET /api/v1/orders/couriers
     */
    suspend fun getCourierOrders(
        token: String,
        page: Int = 0,
        size: Int = 50
    ): NetworkResult<PageOrder> {
        return safeApiCall {
            apiService.getCourierOrders("Bearer $token", page, size)
        }
    }

    /**
     * Report an issue with a parcel.
     * POST /api/v1/orders/{orderId}/issue
     */
    suspend fun reportCourierIssue(
        orderId: String,
        payload: Map<String, Any>,
        token: String
    ): NetworkResult<ApiResponse> {
        return safeApiCall {
            val url = ApiEndpoints.getCourierIssueUrl(orderId)
            apiService.reportCourierIssue(url, payload, "Bearer $token")
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Locker Transactions – Sender
    // ─────────────────────────────────────────────────────────────
    suspend fun verifyReservation(
        request: VerifyReservationRequest,
        token: String
    ): NetworkResult<VerifyReservationBody> {
        return when (val result = safeApiCall { apiService.verifyReservation(request, "Bearer $token") }) {
            is NetworkResult.Success -> {
                result.data.body?.let { NetworkResult.Success(it) } 
                    ?: NetworkResult.Error("Empty response body", 204)
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.Loading -> NetworkResult.Loading() // Should not happen here
        }
    }

    /**
     * Sender drop-off — multipart/form-data matching backend's SenderDropOffRequest:
     *   orderId: UUID (required)
     *   cellId: UUID (required)
     *   photos: List<MultipartFile> (nullable per schema — sends placeholder)
     */
    suspend fun senderDropoff(
        orderId: String,
        cellId: String,
        token: String
    ): NetworkResult<TransactionResponse> {
        return safeApiCall {
            val orderIdBody = orderId.toRequestBody("text/plain".toMediaType())
            val cellIdBody = cellId.toRequestBody("text/plain".toMediaType())

            // Load placeholder photo from assets (per spec Section 12)
            // TODO [CAMERA]: Replace getPlaceholderPhoto() with actual camera capture
            val photos = try {
                val bytes = context.assets.open("placeholder_deposit.jpg").readBytes()
                val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData(
                    name = "photos",
                    filename = "placeholder_deposit.jpg",
                    body = requestBody
                )
                listOf(part)
            } catch (e: Exception) {
                Log.w(TAG, "Placeholder photo not found in assets, sending without photo", e)
                emptyList<MultipartBody.Part>()
            }

            val response = apiService.senderDropoff(orderIdBody, cellIdBody, photos, "Bearer $token")
            // Create a pseudo-TransactionResponse since senderDropoff only returns void body
            Response.success(
                TransactionResponse(
                    success = response.body()?.success ?: false,
                    message = response.body()?.message ?: ""
                )
            )
        }
    }

    /**
     * Courier pickup at source locker — POST /api/v1/transactions/courier/pickup
     * No body — courier identity from JWT.
     */
    suspend fun courierPickupFromLocker(
        lockerId: String,
        token: String
    ): NetworkResult<com.blitztech.pudokiosk.data.api.dto.courier.CourierPickupResponseDto> {
        return when (val result = safeApiCall { apiService.courierPickupFromLocker(lockerId, "Bearer $token") }) {
            is NetworkResult.Success -> {
                result.data.body?.let { NetworkResult.Success(it) }
                    ?: NetworkResult.Error("Empty response body", 204)
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.Loading -> NetworkResult.Loading()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Locker Service – Cell sync & heartbeat (device-level JWT)
    // ─────────────────────────────────────────────────────────────

    /**
     * Fetches all cells for a locker from the backend.
     * Used by LockerSyncWorker to populate the local Room DB.
     */
    suspend fun getLockerCells(
        lockerId: String,
        token: String
    ): NetworkResult<List<CellDto>> {
        val url = ApiEndpoints.getLockerCellsUrl(lockerId)
        return when (val result = safeApiCall { 
            apiService.getLockerCells(url, ApiEndpoints.KIOSK_API_KEY, ApiEndpoints.KIOSK_API_SERVICE) 
        }) {
            is NetworkResult.Success -> {
                NetworkResult.Success(result.data.body ?: emptyList())
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.Loading -> NetworkResult.Loading()
        }
    }

    /**
     * Reports kiosk heartbeat to the backend.
     * PATCH /api/v1/lockers/{lockerId}/status?status=ONLINE
     */
    suspend fun patchLockerStatus(
        lockerId: String,
        status: String,
        token: String
    ): NetworkResult<ApiResponse> {
        val url = ApiEndpoints.getLockerStatusUrl(lockerId)
        return safeApiCall { 
            apiService.patchLockerStatus(url, status, ApiEndpoints.KIOSK_API_KEY, ApiEndpoints.KIOSK_API_SERVICE) 
        }
    }

    /**
     * Reports a cell as MAINTENANCE after a failed RS485 unlock (status 0x00).
     * PATCH /api/v1/cells/{cellId}/status  { "status": "MAINTENANCE" }
     */
    suspend fun reportCellMaintenance(
        cellId: String,
        token: String
    ): NetworkResult<ApiResponse> {
        val url = ApiEndpoints.getCellStatusUrl(cellId)
        return safeApiCall {
            apiService.patchCellStatus(url, mapOf("status" to "MAINTENANCE"), "Bearer $token")
        }
    }

    /**
     * Fetches the N nearest lockers to the given coordinates.
     * GET /api/v1/lockers/nearest/multiple?latitude=...&longitude=...&limit=...
     */
    suspend fun getNearestLockers(
        latitude: Double,
        longitude: Double,
        token: String,
        limit: Int = 5
    ): NetworkResult<List<com.blitztech.pudokiosk.data.api.dto.locker.NearestLockerResult>> {
        return when (val result = safeApiCall {
            apiService.getNearestLockers(latitude, longitude, limit, "Bearer $token")
        }) {
            is NetworkResult.Success -> {
                NetworkResult.Success(result.data.body ?: emptyList())
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.Loading -> NetworkResult.Loading()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Security photos
    // ─────────────────────────────────────────────────────────────
    suspend fun uploadSecurityPhoto(
        photoFile: java.io.File,
        reason: String,
        referenceId: String,
        userId: String,
        kioskId: String,
        capturedAt: Long
    ): NetworkResult<ApiResponse> {
        return safeApiCall {
            val requestBody = photoFile.asRequestBody("image/jpeg".toMediaType())
            val photoPart = okhttp3.MultipartBody.Part.createFormData(
                "photo", photoFile.name, requestBody
            )
            fun textPart(value: String) = value.toRequestBody("text/plain".toMediaType())
            apiService.uploadSecurityPhoto(
                photo = photoPart,
                reason = textPart(reason),
                referenceId = textPart(referenceId),
                userId = textPart(userId),
                kioskId = textPart(kioskId),
                capturedAt = textPart(capturedAt.toString())
            )
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Network helpers
    // ─────────────────────────────────────────────────────────────
    private suspend fun <T> safeApiCall(apiCall: suspend () -> Response<T>): NetworkResult<T> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiCall()
                if (response.isSuccessful) {
                    response.body()?.let { body ->
                        NetworkResult.Success(body)
                    } ?: NetworkResult.Error("Empty response body")
                } else {
                    val errorMessage = parseErrorResponse(response)
                    NetworkResult.Error(errorMessage)
                }
            } catch (exception: Exception) {
                Log.e(TAG, "API call failed", exception)
                val errorMessage = when (exception) {
                    is IOException -> "Network error. Please check your connection."
                    is retrofit2.HttpException -> "Server error: ${exception.code()}"
                    is com.squareup.moshi.JsonDataException -> "Invalid response format"
                    else -> "An unexpected error occurred: ${exception.message}"
                }
                NetworkResult.Error(errorMessage)
            }
        }
    }

    private fun <T> parseErrorResponse(response: Response<T>): String {
        return try {
            val errorBody = response.errorBody()?.string()
            if (errorBody != null) {
                val errorAdapter = moshi.adapter(ErrorResponse::class.java)
                val errorResponse = errorAdapter.fromJson(errorBody)
                errorResponse?.getUserFriendlyMessage() ?: "Server error: ${response.code()}"
            } else {
                "Server error: ${response.code()}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse error response", e)
            "Server error: ${response.code()}"
        }
    }

    private suspend fun <T> safeApiCallWithRetry(
        apiCall: suspend () -> Response<T>
    ): NetworkResult<T> {
        return withContext(Dispatchers.IO) {
            repeat(MAX_RETRY_ATTEMPTS) { attempt ->
                try {
                    val response = apiCall()
                    if (response.isSuccessful) {
                        response.body()?.let { body ->
                            return@withContext NetworkResult.Success(body)
                        } ?: return@withContext NetworkResult.Error("Empty response body")
                    } else if (attempt == MAX_RETRY_ATTEMPTS - 1) {
                        val errorBody = response.errorBody()?.string()
                        val errorMessage = try {
                            val adapter = moshi.adapter(ApiResponse::class.java)
                            adapter.fromJson(errorBody ?: "")?.message ?: "Unknown error"
                        } catch (e: Exception) {
                            "Error: ${response.code()} - ${response.message()}"
                        }
                        return@withContext NetworkResult.Error(errorMessage, response.code())
                    }
                } catch (e: IOException) {
                    if (attempt == MAX_RETRY_ATTEMPTS - 1) {
                        Log.e(TAG, "Network error after $MAX_RETRY_ATTEMPTS attempts", e)
                        return@withContext NetworkResult.Error("Unable to connect. Please try again later.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error", e)
                    return@withContext NetworkResult.Error("An unexpected error occurred: ${e.message}")
                }
                delay(RETRY_DELAY_MS * (attempt + 1))
            }
            NetworkResult.Error("Unable to connect after multiple attempts. Please try again later.")
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Kiosk Provisioning (no auth token — daily OTP in request body)
    // ─────────────────────────────────────────────────────────────
    suspend fun provisionKiosk(
        request: com.blitztech.pudokiosk.data.api.dto.kiosk.KioskProvisionRequest
    ): NetworkResult<com.blitztech.pudokiosk.data.api.dto.kiosk.KioskProvisionApiResponse> {
        return safeApiCall { apiService.provisionKiosk(request) }
    }
}