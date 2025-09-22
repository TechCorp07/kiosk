package com.blitztech.pudokiosk.deviceio.rs485

/**
 * Winnsen Smart Locker Protocol Implementation
 * Custom protocol for STM32L412-based locker control boards
 *
 * Frame Format: [Header] [Length] [Function] [Station] [Lock] [Frame End]
 * - Header: 0x90
 * - Length: 0x06 for commands, 0x07 for responses
 * - Function: 0x05 (unlock), 0x85 (unlock response), 0x12 (status), 0x92 (status response)
 * - Station: 0-3 (based on DIP switch: 00, 01, 10, 11)
 * - Lock: 1-16 (lock number on board)
 * - Frame End: 0x03
 */
object WinnsenProtocol {

    // Protocol constants
    private const val FRAME_HEADER = 0x90.toByte()
    private const val FRAME_END = 0x03.toByte()
    private const val CMD_LENGTH = 0x06.toByte()
    private const val RESP_LENGTH = 0x07.toByte()

    // Function codes
    private const val FUNC_UNLOCK = 0x05.toByte()
    private const val FUNC_UNLOCK_RESP = 0x85.toByte()
    private const val FUNC_STATUS = 0x12.toByte()
    private const val FUNC_STATUS_RESP = 0x92.toByte()

    // Status codes
    const val STATUS_SUCCESS = 0x01.toByte()
    const val STATUS_FAILURE = 0x00.toByte()
    const val STATUS_OPEN = 0x01.toByte()
    const val STATUS_CLOSED = 0x00.toByte()

    /**
     * Create unlock command frame
     * Send: 0x90 0x06 0x05 [Station] [Lock] 0x03
     */
    fun createUnlockCommand(station: Int, lockNumber: Int): ByteArray {
        require(station in 0..3) { "Station must be 0-3, got $station" }
        require(lockNumber in 1..16) { "Lock number must be 1-16, got $lockNumber" }

        return byteArrayOf(
            FRAME_HEADER,
            CMD_LENGTH,
            FUNC_UNLOCK,
            station.toByte(),
            lockNumber.toByte(),
            FRAME_END
        )
    }

    /**
     * Create status check command frame
     * Send: 0x90 0x06 0x12 [Station] [Lock] 0x03
     */
    fun createStatusCommand(station: Int, lockNumber: Int): ByteArray {
        require(station in 0..3) { "Station must be 0-3, got $station" }
        require(lockNumber in 1..16) { "Lock number must be 1-16, got $lockNumber" }

        return byteArrayOf(
            FRAME_HEADER,
            CMD_LENGTH,
            FUNC_STATUS,
            station.toByte(),
            lockNumber.toByte(),
            FRAME_END
        )
    }

    /**
     * Parse unlock response frame
     * Receive: 0x90 0x07 0x85 [Station] [Lock] [Status] 0x03
     */
    fun parseUnlockResponse(response: ByteArray): UnlockResult? {
        if (response.size != 7) return null
        if (response[0] != FRAME_HEADER) return null
        if (response[1] != RESP_LENGTH) return null
        if (response[2] != FUNC_UNLOCK_RESP) return null
        if (response[6] != FRAME_END) return null

        return UnlockResult(
            station = response[3].toInt() and 0xFF,
            lockNumber = response[4].toInt() and 0xFF,
            success = response[5] == STATUS_SUCCESS,
            isOpen = response[5] == STATUS_SUCCESS
        )
    }

    /**
     * Parse status response frame
     * Receive: 0x90 0x07 0x92 [Station] [Status] [Lock] 0x03
     * Note: Different order than unlock response - Status comes before Lock
     */
    fun parseStatusResponse(response: ByteArray): StatusResult? {
        if (response.size != 7) return null
        if (response[0] != FRAME_HEADER) return null
        if (response[1] != RESP_LENGTH) return null
        if (response[2] != FUNC_STATUS_RESP) return null
        if (response[6] != FRAME_END) return null

        return StatusResult(
            station = response[3].toInt() and 0xFF,
            isOpen = response[4] == STATUS_OPEN,
            lockNumber = response[5].toInt() and 0xFF
        )
    }

    /**
     * Validate response matches sent command
     */
    fun validateResponse(command: ByteArray, response: ByteArray): Boolean {
        if (command.size < 6 || response.size < 6) return false

        val cmdStation = command[3]
        val cmdLock = command[4]
        val cmdFunction = command[2]

        when (cmdFunction) {
            FUNC_UNLOCK -> {
                val result = parseUnlockResponse(response) ?: return false
                return result.station == (cmdStation.toInt() and 0xFF) &&
                        result.lockNumber == (cmdLock.toInt() and 0xFF)
            }
            FUNC_STATUS -> {
                val result = parseStatusResponse(response) ?: return false
                return result.station == (cmdStation.toInt() and 0xFF) &&
                        result.lockNumber == (cmdLock.toInt() and 0xFF)
            }
            else -> return false
        }
    }

    /**
     * Convert response to hex string for debugging
     */
    fun toHexString(data: ByteArray): String {
        return data.joinToString(" ") { "%02X".format(it) }
    }
}

/**
 * Result of unlock operation
 */
data class UnlockResult(
    val station: Int,
    val lockNumber: Int,
    val success: Boolean,
    val isOpen: Boolean
)

/**
 * Result of status check operation
 */
data class StatusResult(
    val station: Int,
    val lockNumber: Int,
    val isOpen: Boolean
)