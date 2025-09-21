package com.blitztech.pudokiosk.data.net

import retrofit2.http.*

data class OutboxEventDTO(
    val idempotencyKey: String,
    val type: String,
    val payloadJson: String,
    val createdAt: Long
)
data class DeviceConfigResponse(
    val version: Long,
    val json: String
)
data class EventsBulkRequest(val events: List<OutboxEventDTO>)
data class EventsBulkResponse(val delivered: List<String>) // keys acked
data class OtpRequest(val phone: String)
data class OtpVerifyRequest(val phone: String, val otp: String)
data class OtpVerifyResponse(val userId: String, val firstTime: Boolean)

data class ParcelItem(val parcelId: String, val lockerId: String, val size: String, val tracking: String)
data class ParcelsResponse(val parcels: List<ParcelItem>)

interface ApiService {
    @POST("events/bulk")
    suspend fun postEvents(@Body body: EventsBulkRequest): EventsBulkResponse

    @POST("auth/otp/request")
    suspend fun requestOtp(@Body body: OtpRequest)

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body body: OtpVerifyRequest): OtpVerifyResponse

    @GET("device/config")
    suspend fun fetchConfig(@Query("deviceId") deviceId: String): DeviceConfigResponse

    @GET("recipient/parcels")
    suspend fun recipientParcels(@Query("userId") userId: String): ParcelsResponse
}
