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
 * Response (unlock): [0x90, 0x07, 0x85, station, lock_slot, status, 0x03]
 * Response (status): [0x90, 0x07, 0x92, station, status, lock_slot, 0x03]  <- NOTE: status/lock swapped!
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
    const val STATUS_COOLDOWN: Byte = 0x02.toByte()  // Lock rejected: duty-cycle cooldown active

    // Configuration
    const val MIN_LOCK = 1
    const val MAX_LOCK = 64           // 4 boards × 16 locks
    const val LOCKS_PER_BOARD = 16
    const val NUM_BOARDS = 4
    const val RESPONSE_TIMEOUT_MS = 3000L

    // Station bytes for each board (DIP switch addresses)
    val STATION_BYTES: Array<Byte> = arrayOf(
        0x01.toByte(),  // Board 1: locks  1-16
        0x02.toByte(),  // Board 2: locks 17-32
        0x03.toByte(),  // Board 3: locks 33-48
        0x04.toByte()   // Board 4: locks 49-64
    )

    // Named station constants for convenience
    val STATION_0: Byte get() = STATION_BYTES[0]  // Board 1
    val STATION_1: Byte get() = STATION_BYTES[1]  // Board 2
    val STATION_2: Byte get() = STATION_BYTES[2]  // Board 3
    val STATION_3: Byte get() = STATION_BYTES[3]  // Board 4

    /**
     * Returns the RS485 station byte for a given global lock number (1-64).
     * Board 1 = station 0x01 (locks  1-16)
     * Board 2 = station 0x02 (locks 17-32)
     * Board 3 = station 0x03 (locks 33-48)
     * Board 4 = station 0x04 (locks 49-64)
     */
    fun stationForLock(lockNumber: Int): Byte {
        val boardIndex = (lockNumber - 1) / LOCKS_PER_BOARD
        return STATION_BYTES[boardIndex.coerceIn(0, NUM_BOARDS - 1)]
    }

    /**
     * Converts a global lock number (1-64) to a per-board slot byte (1-16).
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
        val station: Byte,   // always set explicitly via stationForLock()
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
     *
     * IMPORTANT: Field order differs by response type!
     * Unlock response (0x85): [header, length, func, station, lockNumber, status, end]
     * Status response (0x92): [header, length, func, station, status, lockNumber, end]
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
                    val function = data[2]
                    // Status response (0x92) has fields 4,5 swapped vs unlock response (0x85)
                    // Unlock: [header, len, 0x85, station, lock, status, end]
                    // Status: [header, len, 0x92, station, status, lock, end]
                    val lockNumber: Byte
                    val status: Byte

                    if (function == FUNC_STATUS_RESP) {
                        // Status response: data[4] = status, data[5] = lockNumber
                        status = data[4]
                        lockNumber = data[5]
                    } else {
                        // Unlock response (and any other): data[4] = lockNumber, data[5] = status
                        lockNumber = data[4]
                        status = data[5]
                    }

                    ResponseFrame(
                        header = data[0],
                        length = data[1],
                        function = function,
                        station = data[3],
                        lockNumber = lockNumber,
                        status = status,
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
                    (station in STATION_BYTES || station == 0x00.toByte()) && // accept all 4 board stations or broadcast
                    frameEnd == FRAME_END &&
                    lockNumber in 1..LOCKS_PER_BOARD // slot is always 1-16 per board
        }

        fun toHexString(): String {
            // Reconstruct the raw wire bytes (respecting field order swap)
            return if (function == FUNC_STATUS_RESP) {
                byteArrayOf(header, length, function, station, status, lockNumber, frameEnd)
            } else {
                byteArrayOf(header, length, function, station, lockNumber, status, frameEnd)
            }.joinToString(" ") { "%02X".format(it) }
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
        COOLDOWN(STATUS_COOLDOWN, "Cooldown Active"),
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