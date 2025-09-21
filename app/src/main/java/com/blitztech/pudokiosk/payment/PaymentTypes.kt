package com.blitztech.pudokiosk.payment

sealed class PaymentResult {
    data class Approved(val authCode: String? = null): PaymentResult()
    data class Declined(val reason: String): PaymentResult()
    data class Error(val message: String): PaymentResult()
}

data class PaymentRequest(val amountCents: Int, val currency: String = "USD")
