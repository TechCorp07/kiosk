package com.blitztech.pudokiosk.deviceio.rs485

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Locker Controller for STM32L412-based boards using Winnsen Protocol
 *
 * Configuration:
 * - 4 boards max (stations 0-3) based on DIP switch settings: 00, 01, 10, 11
 * - 16 locks per board (numbered 1-16)
 * - Total capacity: 64 locks (M1-M64)
 * - Communication: RS485 at 9600 baud, 8N1
 *
 * Default Mapping:
 * - Station 0 (DIP: 00): M1-M16   (locks 1-16)
 * - Station 1 (DIP: 01): M17-M32  (locks 1-16)
 * - Station 2 (DIP: 10): M33-M48  (locks 1-16)
 * - Station 3 (DIP: 11): M49-M64  (locks 1-16)
 */
class LockerController(
    private val ctx: Context,
    private val simulate: Boolean = false,
    private val customMapping: Map<String, Pair<Int, Int>>? = null
) {
    private val serial by lazy { SerialRs485(ctx) }

    companion object {
        private const val TAG = "LockerController"
        private const val LOCKS_PER_BOARD = 16
        private const val MAX_STATIONS = 4
        private const val COMMUNICATION_TIMEOUT_MS = 800
        private const val MAX_RETRIES = 2
    }

    /**
     * Map locker ID to (station, lockNumber) pair
     * Examples:
     * - "M1" -> (0, 1)   - Station 0, Lock 1
     * - "M16" -> (0, 16) - Station 0, Lock 16
     * - "M17" -> (1, 1)  - Station 1, Lock 1
     * - "M64" -> (3, 16) - Station 3, Lock 16
     */
    private fun mapToStationLock(lockerId: String): Pair<Int, Int> {
        // Check custom mapping first
        customMapping?.get(lockerId)?.let { return it }

        // Default range-based mapping
        val numericPart = lockerId.filter { it.isDigit() }.toIntOrNull()
            ?: throw IllegalArgumentException("Invalid locker ID format: $lockerId")

        require(numericPart in 1..64) {
            "Locker number must be 1-64, got $numericPart in ID: $lockerId"
        }

        // Calculate station and lock number
        val station = (numericPart - 1) / LOCKS_PER_BOARD  // 0-3
        val lockNumber = ((numericPart - 1) % LOCKS_PER_BOARD) + 1  // 1-16

        return Pair(station, lockNumber)
    }

    /**
     * Open a specific locker
     * @param lockerId Locker identifier (e.g., "M12", "M25")
     * @param retries Number of retry attempts on failure
     * @return true if unlock was successful, false otherwise
     */
    suspend fun openLocker(lockerId: String, retries: Int = MAX_RETRIES): Boolean =
        withContext(Dispatchers.IO) {
            if (simulate) {
                Log.d(TAG, "SIMULATED: Opening locker $lockerId")
                return@withContext true
            }

            try {
                val (station, lockNumber) = mapToStationLock(lockerId)
                Log.d(TAG, "Opening locker $lockerId -> Station $station, Lock $lockNumber")

                serial.open(baud = 9600)

                repeat(retries + 1) { attempt ->
                    try {
                        val command = WinnsenProtocol.createUnlockCommand(station, lockNumber)
                        Log.d(TAG, "Attempt ${attempt + 1}: Sending unlock command: ${WinnsenProtocol.toHexString(command)}")

                        val response = serial.writeRead(
                            command = command,
                            expectedResponseSize = 7,
                            timeoutMs = COMMUNICATION_TIMEOUT_MS
                        )

                        Log.d(TAG, "Received response: ${WinnsenProtocol.toHexString(response)}")

                        // Validate and parse response
                        if (WinnsenProtocol.validateResponse(command, response)) {
                            val result = WinnsenProtocol.parseUnlockResponse(response)
                            if (result != null && result.success) {
                                Log.i(TAG, "Successfully opened locker $lockerId (Station ${result.station}, Lock ${result.lockNumber})")
                                return@withContext true
                            } else {
                                Log.w(TAG, "Unlock command failed for locker $lockerId: ${result?.success}")
                            }
                        } else {
                            Log.w(TAG, "Invalid response for locker $lockerId on attempt ${attempt + 1}")
                        }

                    } catch (e: Exception) {
                        Log.w(TAG, "Communication error on attempt ${attempt + 1} for locker $lockerId: ${e.message}")
                        if (attempt < retries) {
                            kotlinx.coroutines.delay(150) // Wait before retry
                        }
                    }
                }

                Log.e(TAG, "Failed to open locker $lockerId after ${retries + 1} attempts")
                return@withContext false

            } catch (e: Exception) {
                Log.e(TAG, "Error opening locker $lockerId: ${e.message}", e)
                return@withContext false
            }
        }

    /**
     * Check if a locker is closed (door sensor status)
     * @param lockerId Locker identifier
     * @return true if door is closed, false if open
     */
    suspend fun isClosed(lockerId: String): Boolean = withContext(Dispatchers.IO) {
        if (simulate) {
            Log.d(TAG, "SIMULATED: Checking locker $lockerId status")
            return@withContext true
        }

        try {
            val (station, lockNumber) = mapToStationLock(lockerId)
            Log.d(TAG, "Checking locker $lockerId status -> Station $station, Lock $lockNumber")

            serial.open(baud = 9600)

            val command = WinnsenProtocol.createStatusCommand(station, lockNumber)
            Log.d(TAG, "Sending status command: ${WinnsenProtocol.toHexString(command)}")

            val response = serial.writeRead(
                command = command,
                expectedResponseSize = 7,
                timeoutMs = COMMUNICATION_TIMEOUT_MS
            )

            Log.d(TAG, "Received status response: ${WinnsenProtocol.toHexString(response)}")

            if (WinnsenProtocol.validateResponse(command, response)) {
                val result = WinnsenProtocol.parseStatusResponse(response)
                if (result != null) {
                    val isClosed = !result.isOpen
                    Log.d(TAG, "Locker $lockerId status: ${if (isClosed) "CLOSED" else "OPEN"}")
                    return@withContext isClosed
                }
            }

            Log.w(TAG, "Invalid status response for locker $lockerId")

        } catch (e: Exception) {
            Log.w(TAG, "Error checking locker $lockerId status: ${e.message}")
        }

        // On any error, assume closed to prevent system deadlock
        Log.d(TAG, "Defaulting to CLOSED status for locker $lockerId (error fallback)")
        return@withContext true
    }

    /**
     * Test communication with a specific station
     * @param station Station number (0-3)
     * @return true if station responds correctly
     */
    suspend fun testStation(station: Int): Boolean = withContext(Dispatchers.IO) {
        require(station in 0 until MAX_STATIONS) { "Station must be 0-${MAX_STATIONS-1}, got $station" }

        try {
            serial.open(baud = 9600)

            // Test with lock 1 status check
            val command = WinnsenProtocol.createStatusCommand(station, 1)
            val response = serial.writeRead(
                command = command,
                expectedResponseSize = 7,
                timeoutMs = COMMUNICATION_TIMEOUT_MS
            )

            val isValid = WinnsenProtocol.validateResponse(command, response)
            Log.d(TAG, "Station $station test: ${if (isValid) "PASS" else "FAIL"}")

            return@withContext isValid

        } catch (e: Exception) {
            Log.w(TAG, "Station $station test failed: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Get system status for all configured stations
     * @return Map of station number to online status
     */
    suspend fun getSystemStatus(): Map<Int, Boolean> = withContext(Dispatchers.IO) {
        val status = mutableMapOf<Int, Boolean>()

        for (station in 0 until MAX_STATIONS) {
            status[station] = testStation(station)
        }

        return@withContext status
    }

    /**
     * Get mapping information for debugging
     */
    fun getLockerMapping(lockerId: String): String {
        return try {
            val (station, lockNumber) = mapToStationLock(lockerId)
            "Locker $lockerId -> Station $station (DIP: ${getDipSetting(station)}), Lock $lockNumber"
        } catch (e: Exception) {
            "Invalid locker ID: $lockerId (${e.message})"
        }
    }

    private fun getDipSetting(station: Int): String = when (station) {
        0 -> "00"
        1 -> "01"
        2 -> "10"
        3 -> "11"
        else -> "??"
    }

    /**
     * Close serial connection and cleanup resources
     */
    suspend fun close() {
        try {
            serial.close()
            Log.d(TAG, "Locker controller closed")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing locker controller: ${e.message}")
        }
    }
}