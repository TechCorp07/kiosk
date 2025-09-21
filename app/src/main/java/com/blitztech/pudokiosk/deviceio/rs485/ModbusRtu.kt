package com.blitztech.pudokiosk.deviceio.rs485

import kotlinx.coroutines.delay

object ModbusRtu {
    // CRC-16 (Modbus)
    fun crc16(data: ByteArray): UShort {
        var crc = 0xFFFF
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            for (i in 0 until 8) {
                val lsb = crc and 0x0001
                crc = crc shr 1
                if (lsb == 1) crc = crc xor 0xA001
            }
        }
        return crc.toUShort()
    }

    // Build a "Write Single Coil" (0x05) frame: [addr, 0x05, hi, lo, FF, 00, CRClo, CRChi]
    fun writeSingleCoil(addr: Int, coil: Int, on: Boolean): ByteArray {
        val hi = (coil shr 8) and 0xFF
        val lo = coil and 0xFF
        val valHi = if (on) 0xFF else 0x00
        val valLo = 0x00
        val pdu = byteArrayOf(addr.toByte(), 0x05, hi.toByte(), lo.toByte(), valHi.toByte(), valLo.toByte())
        val crc = crc16(pdu).toInt()
        val loCrc = (crc and 0xFF).toByte()
        val hiCrc = ((crc shr 8) and 0xFF).toByte()
        return pdu + byteArrayOf(loCrc, hiCrc)
    }

    // Build "Read Coils" (0x01) request: startAddr + count
    fun readCoils(addr: Int, start: Int, count: Int): ByteArray {
        val hiStart = (start shr 8) and 0xFF
        val loStart = start and 0xFF
        val hiCnt = (count shr 8) and 0xFF
        val loCnt = count and 0xFF
        val pdu = byteArrayOf(addr.toByte(), 0x01,
            hiStart.toByte(), loStart.toByte(),
            hiCnt.toByte(), loCnt.toByte())
        val crc = crc16(pdu).toInt()
        val loCrc = (crc and 0xFF).toByte()
        val hiCrc = ((crc shr 8) and 0xFF).toByte()
        return pdu + byteArrayOf(loCrc, hiCrc)
    }

}
