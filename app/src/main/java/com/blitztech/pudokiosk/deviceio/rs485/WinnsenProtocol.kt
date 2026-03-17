package com.blitztech.pudokiosk.deviceio.rs485

import android.util.Log

/**
 * Winnsen Smart Locker Protocol Implementation
 *
 * Protocol Configuration:
 * - Baud Rate: 9600, 8N1
 * - Station 0: Locks 1-16  (first board)
 * - Station 1: Locks 17-32 (second board)
 * - Frame Header: 0x90, Frame End: 0x03
 *
 * Frame Structure:
 * Command:  [0x90, 0x06, function, station, lock_slot, 0x03]
 * Response: [0x90, 0x07, function, station, lock_slot, status, 0x03]
 *
 * lock_slot is always 1-16 relative to the board (station).
 * Global lock number 17 → station=1, slot=1; lock 32 → station=1, slot=16.
 */
object WinnsenProtocol {
    private const val TAG = "WinnsenProtocol"

    // Protocol Constants
    const val FRAME_HEADER: Byte = 0x90.toByte()
    const val FRAME_END: Byte = 0x03.toByte()
    const val CMD_LENGTH: Byte = 0x06.toByte()
    const val RESP_LENGTH: Byte = 0x07.toByte()

    // Function Codes
    const val FUNC_UNLOCK: Byte = 0x05.toByte()
    const val FUNC_UNLOCK_RESP: Byte = 0x85.toByte()
    const val FUNC_STATUS: Byte = 0x12.toByte()
    const val FUNC_STATUS_RESP: Byte = 0x92.toByte()

    // Status Codes
    const val STATUS_SUCCESS: Byte = 0x01.toByte()
    const val STATUS_FAILURE: Byte = 0x00.toByte()
    const val STATUS_OPEN: Byte = 0x01.toByte()
    const val STATUS_CLOSED: Byte = 0x00.toByte()

    // Configuration
    const val STATION_0: Byte = 0x00.toByte() // First board  (locks 1-16)
    const val STATION_1: Byte = 0x01.toByte() // Second board (locks 17-32)
    const val MIN_LOCK = 1
    const val MAX_LOCK = 32
    const val LOCKS_PER_BOARD = 16
    const val RESPONSE_TIMEOUT_MS = 2000L

    /**
     * Returns the RS485 station byte for a given global lock number.
     * Locks 1-16  → station 0 (first board)
     * Locks 17-32 → station 1 (second board)
     */
    fun stationForLock(lockNumber: Int): Byte {
        return if (lockNumber <= LOCKS_PER_BOARD) STATION_0 else STATION_1
    }

    /**
     * Converts a global lock number (1-32) to a per-board slot byte (1-16).
     */
    fun lockByteFor(lockNumber: Int): Byte {
        return (((lockNumber - 1) % LOCKS_PER_BOARD) + 1).toByte()
    }

    /**
     * Command Frame - 6 bytes total
     */
    data class CommandFrame(
        val header: Byte = FRAME_HEADER,
        val length: Byte = CMD_LENGTH,
        val function: Byte,
        val station: Byte = STATION_0,
        val lockNumber: Byte,
        val frameEnd: Byte = FRAME_END
    ) {
        fun toByteArray(): ByteArray {
            return byteArrayOf(header, length, function, station, lockNumber, frameEnd)
        }

        fun toHexString(): String {
            return toByteArray().joinToString(" ") { "%02X".format(it) }
        }
    }

    /**
     * Response Frame - 7 bytes total
     */
    data class ResponseFrame(
        val header: Byte,
        val length: Byte,
        val function: Byte,
        val station: Byte,
        val lockNumber: Byte,
        val status: Byte,
        val frameEnd: Byte
    ) {
        companion object {
            fun fromByteArray(data: ByteArray): ResponseFrame? {
                if (data.size != 7) {
                    Log.w(TAG, "Invalid response frame size: ${data.size}, expected 7")
                    return null
                }

                return try {
                    ResponseFrame(
                        header = data[0],
                        length = data[1],
                        function = data[2],
                        station = data[3],
                        lockNumber = data[4],
                        status = data[5],
                        frameEnd = data[6]
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing response frame", e)
                    null
                }
            }
        }

        fun isValid(): Boolean {
            return header == FRAME_HEADER &&
                    length == RESP_LENGTH &&
                    (station == STATION_0 || station == STATION_1) &&
                    frameEnd == FRAME_END &&
                    lockNumber in 1..LOCKS_PER_BOARD // slot is always 1-16 per board
        }

        fun toHexString(): String {
            return byteArrayOf(header, length, function, station, lockNumber, status, frameEnd)
                .joinToString(" ") { "%02X".format(it) }
        }
    }

    /**
     * Lock operation result
     */
    data class LockOperationResult(
        val success: Boolean,
        val lockNumber: Int,
        val status: LockStatus,
        val errorMessage: String? = null,
        val responseTime: Long = 0
    )

    /**
     * Lock status enumeration
     */
    enum class LockStatus(val value: Byte, val displayName: String) {
        OPEN(STATUS_OPEN, "Open"),
        CLOSED(STATUS_CLOSED, "Closed"),
        UNKNOWN(0xFF.toByte(), "Unknown");

        companion object {
            fun fromByte(value: Byte): LockStatus {
                return values().find { it.value == value } ?: UNKNOWN
            }
        }
    }

    /**
     * Command type enumeration
     */
    enum class CommandType(val functionCode: Byte, val responseCode: Byte, val displayName: String) {
        UNLOCK(FUNC_UNLOCK, FUNC_UNLOCK_RESP, "Unlock"),
        STATUS(FUNC_STATUS, FUNC_STATUS_RESP, "Status Check");

        companion object {
            fun fromResponseCode(code: Byte): CommandType? {
                return values().find { it.responseCode == code }
            }
        }
    }

    /**
     * Utility functions
     */
    fun isValidLockNumber(lockNumber: Int): Boolean {
        return lockNumber in MIN_LOCK..MAX_LOCK
    }

    fun createUnlockCommand(lockNumber: Int): CommandFrame? {
        if (!isValidLockNumber(lockNumber)) {
            Log.w(TAG, "Invalid lock number: $lockNumber (valid: $MIN_LOCK-$MAX_LOCK)")
            return null
        }
        return CommandFrame(
            function = FUNC_UNLOCK,
            station = stationForLock(lockNumber),
            lockNumber = lockByteFor(lockNumber)
        )
    }

    fun createStatusCommand(lockNumber: Int): CommandFrame? {
        if (!isValidLockNumber(lockNumber)) {
            Log.w(TAG, "Invalid lock number: $lockNumber (valid: $MIN_LOCK-$MAX_LOCK)")
            return null
        }
        return CommandFrame(
            function = FUNC_STATUS,
            station = stationForLock(lockNumber),
            lockNumber = lockByteFor(lockNumber)
        )
    }

    /**
     * Parse any incoming frame and determine its type
     */
    fun parseIncomingFrame(data: ByteArray): ResponseFrame? {
        if (data.isEmpty()) return null

        // Log incoming data for debugging
        Log.d(TAG, "Parsing frame: ${data.joinToString(" ") { "%02X".format(it) }}")

        // Try to find a valid 7-byte response frame in the data
        for (i in 0..data.size - 7) {
            val frameData = data.sliceArray(i until i + 7)
            val frame = ResponseFrame.fromByteArray(frameData)

            if (frame?.isValid() == true) {
                Log.d(TAG, "Valid frame found at offset $i: ${frame.toHexString()}")
                return frame
            }
        }

        Log.w(TAG, "No valid frame found in data")
        return null
    }
}