package com.blitztech.pudokiosk.data.repository

import kotlinx.coroutines.delay
import kotlin.random.Random

data class PaymentInitResult(val paymentId: String, val status: String) // PENDING|SUCCESS|FAILED
data class LockerAssignment(val lockerId: String, val size: String)

class BackendRepository {
    // Simulate backend payment flow (your real app will call Retrofit here)
    suspend fun initPayment(amountCents: Int): PaymentInitResult {
        delay(500) // pretend network
        return PaymentInitResult(paymentId = "pay_${Random.nextInt(10000,99999)}", status = "SUCCESS")
    }

    // Simulate locker allocation
    suspend fun allocateLocker(size: String): LockerAssignment {
        delay(300)
        val id = when (size) {
            "XS" -> "X01"; "S" -> "S07"; "M" -> "M12"; "L" -> "L03"; "XL" -> "XL2"
            else -> "M12"
        }
        return LockerAssignment(lockerId = id, size = size)
    }
}
