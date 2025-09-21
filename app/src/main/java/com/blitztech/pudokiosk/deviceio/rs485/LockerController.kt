package com.blitztech.pudokiosk.deviceio.rs485

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

class LockerController(private val ctx: Context, private val simulate: Boolean = false) {
    private val serial by lazy { SerialRs485(ctx) }

    // Map your lockerId -> (addr, coil). For MVP we parse numbers from ID like "M12" -> coil 12.
    private fun mapToAddrCoil(lockerId: String): Pair<Int, Int> {
        val digits = lockerId.filter { it.isDigit() }
        val coil = (digits.toIntOrNull() ?: 1).coerceAtLeast(1)
        val addr = 1 // slave id; adjust per your STM32 node id
        return addr to coil
    }

    private fun mapToDoorInput(lockerId: String): Pair<Int, Int> {
        // If your firmware uses different addressing for sensors, adjust here.
        val (addr, coil) = mapToAddrCoil(lockerId)
        return addr to coil
    }

    suspend fun openLocker(lockerId: String, retries: Int = 2): Boolean = withContext(Dispatchers.IO) {
        if (simulate) {
            // pretend we opened it successfully
            return@withContext true
        }

        val (addr, coil) = mapToAddrCoil(lockerId)
        serial.open(baud = 9600) // adjust if needed

        repeat(retries + 1) { attempt ->
            try {
                val frame = ModbusRtu.writeSingleCoil(addr, coil, true)
                // For function 0x05, the slave should echo the request back.
                val resp = serial.writeRead(frame, readBytes = frame.size, timeoutMs = 400)
                if (resp.size >= 6 && resp[0] == frame[0] && resp[1] == 0x05.toByte()) {
                    return@withContext true
                }
            } catch (_: Exception) {
                // wait a bit then retry
                Thread.sleep(120)
            }
        }
        false
    }

    suspend fun isClosed(lockerId: String): Boolean = withContext(Dispatchers.IO) {
        if (simulate) return@withContext true
        val (addr, bitIndex) = mapToDoorInput(lockerId)

        try {
            serial.open(baud = 9600)
            // NOTE: Modbus coil addresses can be 0- or 1-based depending on firmware.
            // Start with bitIndex and count=1. If it reads inverted or off-by-one,
            // try (bitIndex-1) or invert the boolean below.
            val req = ModbusRtu.readCoils(addr, bitIndex, 1)
            // Response: [addr, 0x01, byteCount, data..., CRClo, CRChi]
            // For 1 coil, byteCount = 1 and LSB of first data byte is the coil state.
            val resp = serial.writeRead(req, readBytes = 7, timeoutMs = 400)
            if (resp.size >= 5 && resp[0] == addr.toByte() && resp[1] == 0x01.toByte()) {
                val data = resp[3].toInt() and 0xFF
                val coilOn = (data and 0x01) != 0
                // ASSUMPTION: coilOn == door CLOSED (invert here if your sensor is NC/open)
                return@withContext coilOn
            }
        } catch (_: Exception) {
            // fall through to default
        }
        // On any error, do not block user → assume closed to avoid kiosk deadlock.
        true
    }

    suspend fun close() { serial.close() }
}
