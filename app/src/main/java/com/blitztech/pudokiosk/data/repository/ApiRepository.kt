package com.blitztech.pudokiosk.data.repository

import android.content.Context
import android.util.Log
import com.blitztech.pudokiosk.data.api.ApiService
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.config.ApiConfig
import com.blitztech.pudokiosk.data.api.dto.auth.*
import com.blitztech.pudokiosk.data.api.dto.user.*
import com.blitztech.pudokiosk.data.api.dto.common.*
import com.blitztech.pudokiosk.data.api.dto.location.CityDto
import com.blitztech.pudokiosk.data.api.dto.location.SuburbDto
import com.blitztech.pudokiosk.data.api.dto.order.CreateOrderRequest
import com.blitztech.pudokiosk.data.api.dto.order.CreateOrderResponse
import com.blitztech.pudokiosk.data.api.dto.order.PackageDetails
import com.blitztech.pudokiosk.data.api.dto.order.PaymentRequest
import com.blitztech.pudokiosk.data.api.dto.order.PaymentResponse
import com.blitztech.pudokiosk.data.api.dto.order.Recipient
import com.blitztech.pudokiosk.data.api.dto.order.SenderLocation
import com.blitztech.pudokiosk.data.api.dto.order.OrderDto
import com.blitztech.pudokiosk.data.api.dto.order.PageOrder
import com.blitztech.pudokiosk.data.api.dto.collection.*
import com.blitztech.pudokiosk.data.api.dto.courier.TransactionRequest
import com.blitztech.pudokiosk.data.api.dto.courier.TransactionResponse
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
    suspend fun registerUser(
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
            apiService.registerUser(request)
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
        return safeApiCallWithRetry { apiService.getCities() }
    }

    suspend fun getSuburbs(cityId: String): NetworkResult<List<SuburbDto>> {
        return safeApiCallWithRetry { apiService.getSuburbs(cityId) }
    }

    // ─────────────────────────────────────────────────────────────
    //  Order Service – Orders & payments
    // ─────────────────────────────────────────────────────────────
    suspend fun createOrder(
        packageDetails: PackageDetails,
        recipient: Recipient,
        senderLocation: SenderLocation,
        currency: String,
        token: String?
    ): NetworkResult<CreateOrderResponse> {
        return safeApiCall {
            val request = CreateOrderRequest(
                packageDetails = packageDetails,
                recipient = recipient,
                senderLocation = senderLocation,
                currency = currency
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

    // ─────────────────────────────────────────────────────────────
    //  Order Service – Customer order tracking
    // ─────────────────────────────────────────────────────────────
    suspend fun getLoggedInOrders(token: String, page: Int = 0, size: Int = 20): NetworkResult<PageOrder> {
        return safeApiCall { apiService.getLoggedInOrders("Bearer $token", page, size) }
    }

    suspend fun trackOrder(trackingNumber: String): NetworkResult<OrderDto> {
        return safeApiCall { apiService.trackOrder(trackingNumber) }
    }

    // ─────────────────────────────────────────────────────────────
    //  Order Service – Locker operations (recipient collection)
    // ─────────────────────────────────────────────────────────────
    suspend fun authenticateRecipient(
        request: RecipientAuthRequest
    ): NetworkResult<RecipientAuthResponse> {
        return safeApiCall { apiService.authenticateRecipient(request) }
    }

    suspend fun completePickup(
        request: LockerPickupRequest,
        token: String
    ): NetworkResult<ApiResponse> {
        return safeApiCall { apiService.completePickup(request, "Bearer $token") }
    }

    suspend fun openCell(
        request: LockerOpenRequest,
        token: String
    ): NetworkResult<ApiResponse> {
        return safeApiCall { apiService.openCell(request, "Bearer $token") }
    }

    // ─────────────────────────────────────────────────────────────
    //  Locker Transactions – Courier
    // ─────────────────────────────────────────────────────────────
    suspend fun courierPickup(
        request: TransactionRequest,
        token: String
    ): NetworkResult<TransactionResponse> {
        return safeApiCall { apiService.courierPickup(request, "Bearer $token") }
    }

    suspend fun courierDropoff(
        request: TransactionRequest,
        token: String
    ): NetworkResult<TransactionResponse> {
        return safeApiCall { apiService.courierDropoff(request, "Bearer $token") }
    }

    // ─────────────────────────────────────────────────────────────
    //  Locker Transactions – Sender
    // ─────────────────────────────────────────────────────────────
    suspend fun verifyReservation(
        request: TransactionRequest,
        token: String
    ): NetworkResult<TransactionResponse> {
        return safeApiCall { apiService.verifyReservation(request, "Bearer $token") }
    }

    suspend fun senderDropoff(
        request: TransactionRequest,
        token: String
    ): NetworkResult<TransactionResponse> {
        return safeApiCall { apiService.senderDropoff(request, "Bearer $token") }
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
}