package com.blitztech.pudokiosk.deviceio.rs485

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production Locker Controller for STM32L412-based boards using Winnsen Protocol
 *
 * Configuration:
 * - Single board (station 0) with 16 locks
 * - Communication: RS485 at 9600 baud, 8N1
 * - Device: VID:04E2 PID:1414 Port 2
 *
 * Locker Mapping:
 * - M1-M16 -> Lock numbers 1-16 on station 0
 */
class LockerController(
    private val ctx: Context,
    private val simulate: Boolean = false
) {
    private val rs485Driver by lazy { RS485Driver(ctx) }

    companion object {
        private const val TAG = "LockerController"
        private const val STATION_ADDRESS = 0  // Single board configuration
        private const val MIN_LOCK = 1
        private const val MAX_LOCK = 16
        private const val COMMUNICATION_TIMEOUT_MS = 800
        private const val MAX_RETRIES = 2
    }

    /**
     * Map locker ID to lock number
     * Examples:
     * - "M1" -> 1
     * - "M16" -> 16
     */
    private fun mapToLockNumber(lockerId: String): Int {
        val numericPart = lockerId.filter { it.isDigit() }.toIntOrNull()
            ?: throw IllegalArgumentException("Invalid locker ID format: $lockerId")

        require(numericPart in MIN_LOCK..MAX_LOCK) {
            "Locker number must be $MIN_LOCK-$MAX_LOCK, got $numericPart in ID: $lockerId"
        }

        return numericPart
    }

    /**
     * Initialize the locker controller
     * @return true if initialization successful
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (simulate) {
            Log.d(TAG, "SIMULATED: Locker controller initialized")
            return@withContext true
        }

        try {
            val connected = rs485Driver.connect()
            if (connected) {
                Log.i(TAG, "✅ Locker controller initialized - ${rs485Driver.getDeviceInfo()}")
                return@withContext true
            } else {
                Log.e(TAG, "❌ Failed to connect to STM32L412 board")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Initialization error: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Open a specific locker
     * @param lockerId Locker identifier (e.g., "M1", "M16")
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
                val lockNumber = mapToLockNumber(lockerId)
                Log.d(TAG, "Opening locker $lockerId -> Lock $lockNumber")

                repeat(retries + 1) { attempt ->
                    try {
                        val command = WinnsenProtocol.createUnlockCommand(STATION_ADDRESS, lockNumber)
                        Log.d(TAG, "Attempt ${attempt + 1}: Sending unlock command for lock $lockNumber")

                        Log.w(TAG, "Attempt ${attempt + 1} failed for locker $lockerId")

                    } catch (e: Exception) {
                        Log.w(TAG, "Attempt ${attempt + 1} error for locker $lockerId: ${e.message}")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error opening locker $lockerId: ${e.message}", e)
            }

            Log.e(TAG, "❌ Failed to open locker $lockerId after $retries retries")
            return@withContext false
        }
    /**
     * Close the connection
     */
    suspend fun close() = withContext(Dispatchers.IO) {
        if (!simulate) {
            rs485Driver.disconnect()
        }
        Log.d(TAG, "Locker controller closed")
    }

    /**
     * Get all valid locker IDs for this system
     */
    fun getValidLockerIds(): List<String> {
        return (MIN_LOCK..MAX_LOCK).map { "M$it" }
    }

    /**
     * Check if a locker ID is valid
     */
    fun isValidLockerId(lockerId: String): Boolean {
        return try {
            val lockNumber = mapToLockNumber(lockerId)
            lockNumber in MIN_LOCK..MAX_LOCK
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * System status data class
 */
data class SystemStatus(
    val isConnected: Boolean,
    val deviceInfo: String,
    val totalLocks: Int,
    val communicationTest: Boolean,
    val message: String
)