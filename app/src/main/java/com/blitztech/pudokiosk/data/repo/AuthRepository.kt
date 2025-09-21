package com.blitztech.pudokiosk.data.repo

import com.blitztech.pudokiosk.data.ServiceLocator
import com.blitztech.pudokiosk.data.net.OtpRequest
import com.blitztech.pudokiosk.data.net.OtpVerifyRequest
import com.blitztech.pudokiosk.data.net.OtpVerifyResponse
import kotlinx.coroutines.delay

class AuthRepository(private val useStub: Boolean = false) {

    suspend fun requestOtp(phone: String) {
        if (useStub) { delay(300); return }
        ServiceLocator.api.requestOtp(OtpRequest(phone))
    }

    suspend fun verifyOtp(phone: String, otp: String): OtpVerifyResponse {
        if (useStub) {
            delay(300)
            // First time if phone ends with odd digit, for demo
            val first = phone.takeLast(1).toIntOrNull()?.rem(2) == 1
            return OtpVerifyResponse(userId = "usr_${phone.takeLast(4)}", firstTime = first)
        }
        return ServiceLocator.api.verifyOtp(OtpVerifyRequest(phone, otp))
    }
}
