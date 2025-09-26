package com.blitztech.pudokiosk.deviceio.rs485

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production Locker Controller for Single STM32L412 Board
 *
 * Configuration:
 * - Single board (Station 0, hardcoded)
 * - 16 locks (numbered 1-16)
 * - Communication: RS485 at 9600 baud, 8N1
 * - Protocol: Winnsen Custom Protocol
 *
 * Usage:
 * - openLocker(1) through openLocker(16)
 * - checkLockerStatus(1) through checkLockerStatus(16)
 */
class LockerController(private val context: Context) {

    private val serial by lazy { SerialRs485(context) }

    companion object {
        private const val TAG = "LockerController"
        private const val STATION_ADDRESS = 0 // Hardcoded to Station 0
        private const val MIN_LOCK = 1
        private const val MAX_LOCK = 16
        private const val COMMUNICATION_TIMEOUT_MS = 800L
        private const val MAX_RETRIES = 3
    }

    /**
     * Open a specific locker
     * @param lockNumber Lock number (1-16)
     * @param retries Number of retry attempts (default: MAX_RETRIES)
     * @return true if unlock successful
     */
    suspend fun openLocker(lockNumber: Int, retries: Int = MAX_RETRIES): Boolean = withContext(Dispatchers.IO) {
        if (!isValidLockNumber(lockNumber)) {
            Log.e(TAG, "Invalid lock number: $lockNumber (must be 1-16)")
            return@withContext false
        }

        Log.d(TAG, "Opening locker $lockNumber...")

        for (attempt in 0 until retries) {
            try {
                if (!serial.open()) {
                    Log.w(TAG, "Failed to connect to serial port (attempt ${attempt + 1})")
                    if (attempt < retries - 1) continue else return@withContext false
                }

                val command = WinnsenProtocol.createUnlockCommand(STATION_ADDRESS, lockNumber)
                Log.d(TAG, "Sending unlock command for lock $lockNumber: ${WinnsenProtocol.toHexString(command)}")

                val response = serial.writeRead(
                    command = command,
                    expectedResponseSize = 7,
                    timeoutMs = COMMUNICATION_TIMEOUT_MS
                )

                if (response.isEmpty()) {
                    Log.w(TAG, "No response for lock $lockNumber (attempt ${attempt + 1})")
                    if (attempt < retries - 1) continue else return@withContext false
                }

                Log.d(TAG, "Received unlock response: ${WinnsenProtocol.toHexString(response)}")

                if (WinnsenProtocol.validateResponse(command, response)) {
                    val result = WinnsenProtocol.parseUnlockResponse(response)
                    if (result != null && result.success) {
                        Log.i(TAG, "✅ Locker $lockNumber opened successfully")
                        return@withContext true
                    } else {
                        Log.w(TAG, "Unlock failed for lock $lockNumber: ${result?.success}")
                    }
                } else {
                    Log.w(TAG, "Invalid response for lock $lockNumber")
                }

            } catch (e: Exception) {
                Log.w(TAG, "Error opening lock $lockNumber (attempt ${attempt + 1}): ${e.message}")
            }

            // If not last attempt, continue retrying
            if (attempt < retries - 1) {
                Log.d(TAG, "Retrying unlock for lock $lockNumber...")
            }
        }

        Log.e(TAG, "❌ Failed to open locker $lockNumber after $retries attempts")
        return@withContext false
    }

    /**
     * Check the status of a specific locker
     * @param lockNumber Lock number (1-16)
     * @param retries Number of retry attempts (default: MAX_RETRIES)
     * @return true if locker is closed/locked, false if open
     */
    suspend fun checkLockerStatus(lockNumber: Int, retries: Int = MAX_RETRIES): Boolean = withContext(Dispatchers.IO) {
        if (!isValidLockNumber(lockNumber)) {
            Log.e(TAG, "Invalid lock number: $lockNumber (must be 1-16)")
            return@withContext true // Default to closed on error
        }

        Log.d(TAG, "Checking locker $lockNumber status...")

        for (attempt in 0 until retries) {
            try {
                if (!serial.open()) {
                    Log.w(TAG, "Failed to connect to serial port (attempt ${attempt + 1})")
                    if (attempt < retries - 1) continue else return@withContext true
                }

                val command = WinnsenProtocol.createStatusCommand(STATION_ADDRESS, lockNumber)
                Log.d(TAG, "Sending status command for lock $lockNumber: ${WinnsenProtocol.toHexString(command)}")

                val response = serial.writeRead(
                    command = command,
                    expectedResponseSize = 7,
                    timeoutMs = COMMUNICATION_TIMEOUT_MS
                )

                if (response.isEmpty()) {
                    Log.w(TAG, "No response for lock $lockNumber status (attempt ${attempt + 1})")
                    if (attempt < retries - 1) continue else return@withContext true
                }

                Log.d(TAG, "Received status response: ${WinnsenProtocol.toHexString(response)}")

                if (WinnsenProtocol.validateResponse(command, response)) {
                    val result = WinnsenProtocol.parseStatusResponse(response)
                    if (result != null) {
                        val isClosed = !result.isOpen
                        Log.d(TAG, "Locker $lockNumber status: ${if (isClosed) "CLOSED" else "OPEN"}")
                        return@withContext isClosed
                    }
                } else {
                    Log.w(TAG, "Invalid status response for lock $lockNumber")
                }

            } catch (e: Exception) {
                Log.w(TAG, "Error checking lock $lockNumber status (attempt ${attempt + 1}): ${e.message}")
            }

            // If not last attempt, continue retrying
            if (attempt < retries - 1) {
                Log.d(TAG, "Retrying status check for lock $lockNumber...")
            }
        }

        Log.w(TAG, "Failed to get status for locker $lockNumber after $retries attempts, defaulting to CLOSED")
        return@withContext true // Default to closed on error
    }

    /**
     * Test communication with the locker board
     * @return true if board responds correctly
     */
    suspend fun testCommunication(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Testing communication with locker board...")

        try {
            // Test with lock 1 status check
            val result = checkLockerStatus(1, retries = 1)
            Log.i(TAG, "Communication test: ${if (result != null) "SUCCESS" else "FAILED"}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Communication test failed: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Get system status information
     * @return Map with system information
     */
    suspend fun getSystemStatus(): Map<String, Any> = withContext(Dispatchers.IO) {
        val status = mutableMapOf<String, Any>()

        try {
            val isOnline = testCommunication()
            status["boardOnline"] = isOnline
            status["stationAddress"] = STATION_ADDRESS
            status["totalLocks"] = MAX_LOCK
            status["communicationOk"] = isOnline
            status["serialConnected"] = serial.isOpen()

            if (isOnline) {
                Log.i(TAG, "System status: Board online, $MAX_LOCK locks available")
            } else {
                Log.w(TAG, "System status: Board offline or communication error")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error getting system status: ${e.message}")
            status["error"] = e.message ?: "Unknown error"
        }

        return@withContext status
    }

    /**
     * Close the serial connection
     */
    fun close() {
        try {
            serial.close()
            Log.d(TAG, "Locker controller closed")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing locker controller: ${e.message}")
        }
    }

    /**
     * Validate lock number is within valid range
     */
    private fun isValidLockNumber(lockNumber: Int): Boolean {
        return lockNumber in MIN_LOCK..MAX_LOCK
    }
}