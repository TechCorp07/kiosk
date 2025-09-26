package com.blitztech.pudokiosk.deviceio.rs485

/**
 * Winnsen Smart Locker Protocol Implementation for STM32L412
 *
 * Frame Format: [Header] [Length] [Function] [Station] [Lock] [Frame End]
 * - Header: 0x90
 * - Length: 0x06 for commands, 0x07 for responses
 * - Function: 0x05 (unlock), 0x85 (unlock response), 0x12 (status), 0x92 (status response)
 * - Station: 0 (hardcoded for single board)
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
        require(station == 0) { "Station must be 0 for single board, got $station" }
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
        require(station == 0) { "Station must be 0 for single board, got $station" }
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
        if (!isValidResponseFrame(response, FUNC_UNLOCK_RESP)) {
            return null
        }

        val station = response[3].toInt()
        val lockNumber = response[4].toInt()
        val statusByte = response[5]
        val success = (statusByte == STATUS_SUCCESS)
        val isOpen = (statusByte == STATUS_OPEN)

        return UnlockResult(station, lockNumber, success, isOpen)
    }

    /**
     * Parse status response frame
     * Receive: 0x90 0x07 0x92 [Station] [Status] [Lock] 0x03
     */
    fun parseStatusResponse(response: ByteArray): StatusResult? {
        if (!isValidResponseFrame(response, FUNC_STATUS_RESP)) {
            return null
        }

        val station = response[3].toInt()
        val statusByte = response[4]
        val lockNumber = response[5].toInt()
        val isOpen = (statusByte == STATUS_OPEN)

        return StatusResult(station, lockNumber, isOpen)
    }

    /**
     * Validate response frame format and function code
     */
    fun validateResponse(command: ByteArray, response: ByteArray): Boolean {
        if (response.size != 7) return false
        if (response[0] != FRAME_HEADER) return false
        if (response[1] != RESP_LENGTH) return false
        if (response[6] != FRAME_END) return false

        // Check if response function matches command
        val commandFunction = command[2]
        val expectedResponseFunction = when (commandFunction) {
            FUNC_UNLOCK -> FUNC_UNLOCK_RESP
            FUNC_STATUS -> FUNC_STATUS_RESP
            else -> return false
        }

        return response[2] == expectedResponseFunction
    }

    /**
     * Validate response frame structure
     */
    private fun isValidResponseFrame(response: ByteArray, expectedFunction: Byte): Boolean {
        return response.size == 7 &&
                response[0] == FRAME_HEADER &&
                response[1] == RESP_LENGTH &&
                response[2] == expectedFunction &&
                response[6] == FRAME_END
    }

    /**
     * Convert byte array to hex string for logging
     */
    fun toHexString(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02X".format(it) }
    }

    /**
     * Data class for unlock operation result
     */
    data class UnlockResult(
        val station: Int,
        val lockNumber: Int,
        val success: Boolean,
        val isOpen: Boolean
    )

    /**
     * Data class for status check result
     */
    data class StatusResult(
        val station: Int,
        val lockNumber: Int,
        val isOpen: Boolean
    )
}