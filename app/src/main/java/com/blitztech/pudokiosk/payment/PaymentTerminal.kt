package com.blitztech.pudokiosk.payment

interface PaymentTerminal {
    suspend fun init(): Boolean
    suspend fun pay(req: PaymentRequest, timeoutMs: Long = 60_000): PaymentResult
    suspend fun close()
}
