package com.blitztech.pudokiosk.data.repository

import android.content.Context
import android.util.Log
import com.blitztech.pudokiosk.data.api.ApiService
import com.blitztech.pudokiosk.data.api.NetworkResult
import com.blitztech.pudokiosk.data.api.config.ApiConfig
import com.blitztech.pudokiosk.data.api.dto.auth.*
import com.blitztech.pudokiosk.data.api.dto.user.*
import com.blitztech.pudokiosk.data.api.dto.common.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
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
    }

    // Authentication methods
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

    // User registration methods
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
            val address = Address(city, suburb, street, houseNumber)
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
            // For now, using a placeholder file as mentioned in requirements
            val placeholderContent = "PLACEHOLDER_KYC_DOCUMENT_FOR_KIOSK"
            val tempFile = File(context.cacheDir, "kyc_placeholder.pdf")
            tempFile.writeText(placeholderContent)

            val requestFile = tempFile.asRequestBody("application/pdf".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)

            apiService.uploadKyc(mobileNumber, ApiConfig.KYC_TYPE, filePart)
        }
    }

    // Generic safe API call wrapper
    private suspend fun <T> safeApiCall(apiCall: suspend () -> Response<T>): NetworkResult<T> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiCall()
                Log.d(TAG, "API Response: ${response.code()} - ${response.message()}")

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        NetworkResult.Success(body)
                    } else {
                        NetworkResult.Error("Empty response body")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "API Error: ${response.code()} - $errorBody")

                    // Try to parse error response
                    val errorMessage = try {
                        if (errorBody != null) {
                            val adapter = moshi.adapter(ApiResponse::class.java)
                            val errorResponse = adapter.fromJson(errorBody)
                            errorResponse?.message ?: "Unknown error occurred"
                        } else {
                            "Network error occurred"
                        }
                    } catch (e: Exception) {
                        "Failed to parse error response"
                    }

                    NetworkResult.Error(errorMessage, response.code())
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error", e)
                NetworkResult.Error("Network connection error. Please check your internet connection.")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error", e)
                NetworkResult.Error("An unexpected error occurred: ${e.message}")
            }
        }
    }
}