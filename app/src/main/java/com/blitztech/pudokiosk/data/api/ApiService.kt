package com.blitztech.pudokiosk.data.api

import com.blitztech.pudokiosk.data.api.config.ApiEndpoints
import com.blitztech.pudokiosk.data.api.dto.auth.*
import com.blitztech.pudokiosk.data.api.dto.user.*
import com.blitztech.pudokiosk.data.api.dto.common.*
import com.blitztech.pudokiosk.data.api.dto.location.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    // Authentication endpoints
    @POST(ApiEndpoints.AUTH_LOGIN)
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST(ApiEndpoints.AUTH_OTP_VERIFY)
    suspend fun verifyOtp(@Body request: OtpVerifyRequest): Response<OtpVerifyResponse>  // Make sure this line is correct

    @GET(ApiEndpoints.FORGOT_PIN)
    suspend fun forgotPin(@Query("mobileNumber") mobileNumber: String,
                          @Query("otpMethod") otpMethod: String): Response<ApiResponse>

    @POST(ApiEndpoints.CHANGE_PIN)
    suspend fun changePin(@Body request: PinChangeRequest,
                          @Header("Authorization") token: String): Response<ApiResponse>

    // User endpoints
    @POST(ApiEndpoints.USER_REGISTER)
    suspend fun registerUser(@Body request: SignUpRequest): Response<RegistrationResponse>

    @Multipart
    @POST(ApiEndpoints.USER_KYC_UPLOAD)
    suspend fun uploadKyc(@Path("mobileNumber") mobileNumber: String,
                          @Part("type") type: String,
                          @Part file: MultipartBody.Part): Response<KycResponse>

    // Location endpoints
    @GET("orders/cities")
    suspend fun getCities(): Response<List<CityDto>>

    @GET("orders/cities/{cityId}/suburbs")
    suspend fun getSuburbs(@Path("cityId") cityId: String): Response<List<SuburbDto>>
}