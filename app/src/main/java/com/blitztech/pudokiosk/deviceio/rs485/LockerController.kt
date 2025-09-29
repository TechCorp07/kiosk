package com.blitztech.pudokiosk.deviceio.rs485

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * High-level controller for STM32L412 Smart Locker communication
 *
 * Manages communication with the STM32L412 locker controller board via RS485.
 * Provides convenient methods for lock operations and status checking.
 */
class LockerController(private val context: Context) {

    private val rs485Driver = RS485Driver(context)
    private val communicationLog = mutableListOf<String>()

    companion object {
        private const val TAG = "LockerController"
        private const val MAX_LOG_ENTRIES = 100
        private const val COMMAND_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 500L
    }

    /**
     * Connection status
     */
    var isConnected: Boolean = false
        private set

    /**
     * Initialize and connect to the locker controller
     */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            log("🔌 Connecting to STM32L412 Locker Controller...")

            val success = rs485Driver.connect(baudRate = 9600, portNumber = 1)
            isConnected = success

            if (success) {
                log("✅ Connected successfully!")
                log("📡 Station: ${WinnsenProtocol.STATION_NUMBER}, Locks: ${WinnsenProtocol.MIN_LOCK}-${WinnsenProtocol.MAX_LOCK}")

                // Optional: Perform connection test
                delay(500) // Allow hardware to stabilize

            } else {
                log("❌ Connection failed!")
            }

            success
        } catch (e: Exception) {
            log("❌ Connection error: ${e.message}")
            Log.e(TAG, "Connection error", e)
            isConnected = false
            false
        }
    }

    /**
     * Disconnect from the locker controller
     */
    suspend fun disconnect() {
        rs485Driver.disconnect()
        isConnected = false
        log("🔌 Disconnected from locker controller")
    }

    /**
     * Unlock a specific lock
     */
    suspend fun unlockLock(lockNumber: Int): WinnsenProtocol.LockOperationResult = withContext(Dispatchers.IO) {
        if (!isConnected) {
            return@withContext WinnsenProtocol.LockOperationResult(
                success = false,
                lockNumber = lockNumber,
                status = WinnsenProtocol.LockStatus.UNKNOWN,
                errorMessage = "Not connected to locker controller"
            )
        }

        if (!WinnsenProtocol.isValidLockNumber(lockNumber)) {
            return@withContext WinnsenProtocol.LockOperationResult(
                success = false,
                lockNumber = lockNumber,
                status = WinnsenProtocol.LockStatus.UNKNOWN,
                errorMessage = "Invalid lock number: $lockNumber (must be ${WinnsenProtocol.MIN_LOCK}-${WinnsenProtocol.MAX_LOCK})"
            )
        }

        val command = WinnsenProtocol.createUnlockCommand(lockNumber)
            ?: return@withContext WinnsenProtocol.LockOperationResult(
                success = false,
                lockNumber = lockNumber,
                status = WinnsenProtocol.LockStatus.UNKNOWN,
                errorMessage = "Failed to create unlock command"
            )

        log("🔓 Unlocking lock $lockNumber...")
        log("📤 Sending: ${command.toHexString()}")

        return@withContext sendCommandWithRetry(command, WinnsenProtocol.CommandType.UNLOCK, lockNumber)
    }

    /**
     * Check the status of a specific lock
     */
    suspend fun checkLockStatus(lockNumber: Int): WinnsenProtocol.LockOperationResult = withContext(Dispatchers.IO) {
        if (!isConnected) {
            return@withContext WinnsenProtocol.LockOperationResult(
                success = false,
                lockNumber = lockNumber,
                status = WinnsenProtocol.LockStatus.UNKNOWN,
                errorMessage = "Not connected to locker controller"
            )
        }

        if (!WinnsenProtocol.isValidLockNumber(lockNumber)) {
            return@withContext WinnsenProtocol.LockOperationResult(
                success = false,
                lockNumber = lockNumber,
                status = WinnsenProtocol.LockStatus.UNKNOWN,
                errorMessage = "Invalid lock number: $lockNumber"
            )
        }

        val command = WinnsenProtocol.createStatusCommand(lockNumber)
            ?: return@withContext WinnsenProtocol.LockOperationResult(
                success = false,
                lockNumber = lockNumber,
                status = WinnsenProtocol.LockStatus.UNKNOWN,
                errorMessage = "Failed to create status command"
            )

        log("🔍 Checking status of lock $lockNumber...")
        log("📤 Sending: ${command.toHexString()}")

        return@withContext sendCommandWithRetry(command, WinnsenProtocol.CommandType.STATUS, lockNumber)
    }

    /**
     * Check status of all locks (1-16)
     */
    suspend fun checkAllLockStatuses(): Map<Int, WinnsenProtocol.LockOperationResult> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<Int, WinnsenProtocol.LockOperationResult>()

        log("🔍 Checking status of all locks...")

        for (lockNumber in WinnsenProtocol.MIN_LOCK..WinnsenProtocol.MAX_LOCK) {
            val result = checkLockStatus(lockNumber)
            results[lockNumber] = result

            // Small delay between commands to avoid overwhelming the STM32
            delay(100)
        }

        val openCount = results.values.count { it.status == WinnsenProtocol.LockStatus.OPEN }
        val closedCount = results.values.count { it.status == WinnsenProtocol.LockStatus.CLOSED }
        log("📊 Status summary: $openCount open, $closedCount closed, ${16 - openCount - closedCount} unknown")

        results
    }

    /**
     * Send command with automatic retry logic
     */
    private suspend fun sendCommandWithRetry(
        command: WinnsenProtocol.CommandFrame,
        expectedType: WinnsenProtocol.CommandType,
        lockNumber: Int
    ): WinnsenProtocol.LockOperationResult {
        var lastError: String? = null

        repeat(COMMAND_RETRY_COUNT) { attempt ->
            try {
                val startTime = System.currentTimeMillis()
                val result = sendCommand(command, expectedType, lockNumber)
                val responseTime = System.currentTimeMillis() - startTime

                if (result.success) {
                    if (attempt > 0) {
                        log("✅ Command succeeded on attempt ${attempt + 1}")
                    }
                    return result.copy(responseTime = responseTime)
                } else {
                    lastError = result.errorMessage
                    if (attempt < COMMAND_RETRY_COUNT - 1) {
                        log("⚠️ Attempt ${attempt + 1} failed: ${result.errorMessage}, retrying...")
                        delay(RETRY_DELAY_MS)
                    }
                }
            } catch (e: Exception) {
                lastError = e.message
                Log.e(TAG, "Command attempt ${attempt + 1} failed", e)
                if (attempt < COMMAND_RETRY_COUNT - 1) {
                    delay(RETRY_DELAY_MS)
                }
            }
        }

        log("❌ All $COMMAND_RETRY_COUNT attempts failed. Last error: $lastError")
        return WinnsenProtocol.LockOperationResult(
            success = false,
            lockNumber = lockNumber,
            status = WinnsenProtocol.LockStatus.UNKNOWN,
            errorMessage = "Command failed after $COMMAND_RETRY_COUNT attempts: $lastError"
        )
    }

    /**
     * Send a single command and wait for response
     */
    private suspend fun sendCommand(
        command: WinnsenProtocol.CommandFrame,
        expectedType: WinnsenProtocol.CommandType,
        lockNumber: Int
    ): WinnsenProtocol.LockOperationResult {

        // Clear any pending data
        rs485Driver.clearReceiveBuffer()

        // Send command
        val commandBytes = command.toByteArray()
        val sendSuccess = rs485Driver.sendData(commandBytes)

        if (!sendSuccess) {
            return WinnsenProtocol.LockOperationResult(
                success = false,
                lockNumber = lockNumber,
                status = WinnsenProtocol.LockStatus.UNKNOWN,
                errorMessage = "Failed to send command"
            )
        }

        // Wait for response
        val response = waitForResponse(expectedType, lockNumber)

        if (response == null) {
            return WinnsenProtocol.LockOperationResult(
                success = false,
                lockNumber = lockNumber,
                status = WinnsenProtocol.LockStatus.UNKNOWN,
                errorMessage = "No response received within timeout"
            )
        }

        log("📥 Received: ${response.toHexString()}")

        // Parse response
        val lockStatus = WinnsenProtocol.LockStatus.fromByte(response.status)
        val isSuccess = when (expectedType) {
            WinnsenProtocol.CommandType.UNLOCK -> response.status == WinnsenProtocol.STATUS_SUCCESS
            WinnsenProtocol.CommandType.STATUS -> true // Status commands always succeed if we get a response
        }

        if (isSuccess) {
            log("✅ Lock $lockNumber ${expectedType.displayName.lowercase()} successful - Status: ${lockStatus.displayName}")
        } else {
            log("❌ Lock $lockNumber ${expectedType.displayName.lowercase()} failed - Status: ${lockStatus.displayName}")
        }

        return WinnsenProtocol.LockOperationResult(
            success = isSuccess,
            lockNumber = lockNumber,
            status = lockStatus,
            errorMessage = if (!isSuccess) "Operation failed with status: ${lockStatus.displayName}" else null
        )
    }

    /**
     * Wait for a specific response frame
     */
    private suspend fun waitForResponse(
        expectedType: WinnsenProtocol.CommandType,
        expectedLockNumber: Int
    ): WinnsenProtocol.ResponseFrame? {

        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < WinnsenProtocol.RESPONSE_TIMEOUT_MS) {
            val receivedData = rs485Driver.receiveData(100) // Short timeout for polling

            if (receivedData.isNotEmpty()) {
                val frame = WinnsenProtocol.parseIncomingFrame(receivedData)

                if (frame != null &&
                    frame.function == expectedType.responseCode &&
                    frame.lockNumber.toInt() == expectedLockNumber) {
                    return frame
                }
            }

            delay(10) // Small delay to prevent tight loop
        }

        return null
    }

    /**
     * Test communication with the locker controller
     */
    suspend fun testCommunication(): Boolean = withContext(Dispatchers.IO) {
        log("🧪 Testing communication with locker controller...")

        if (!isConnected) {
            log("❌ Not connected")
            return@withContext false
        }

        // Test with lock 1 status check
        val result = checkLockStatus(1)

        if (result.success) {
            log("✅ Communication test successful!")
            log("📋 Lock 1 status: ${result.status.displayName}")
            return@withContext true
        } else {
            log("❌ Communication test failed: ${result.errorMessage}")
            return@withContext false
        }
    }

    /**
     * Emergency unlock all locks (for safety/maintenance)
     */
    suspend fun emergencyUnlockAll(): Map<Int, WinnsenProtocol.LockOperationResult> = withContext(Dispatchers.IO) {
        log("🚨 EMERGENCY: Unlocking all locks...")

        val results = mutableMapOf<Int, WinnsenProtocol.LockOperationResult>()

        for (lockNumber in WinnsenProtocol.MIN_LOCK..WinnsenProtocol.MAX_LOCK) {
            val result = unlockLock(lockNumber)
            results[lockNumber] = result

            // Small delay between emergency unlocks
            delay(50)
        }

        val successCount = results.values.count { it.success }
        log("🚨 Emergency unlock completed: $successCount/${WinnsenProtocol.MAX_LOCK} locks unlocked")

        results
    }

    /**
     * Get communication log messages
     */
    fun getLogMessages(): List<String> = communicationLog.toList()

    /**
     * Clear communication log
     */
    fun clearLog() {
        communicationLog.clear()
    }

    /**
     * Internal logging function
     */
    private fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message"

        communicationLog.add(logEntry)
        if (communicationLog.size > MAX_LOG_ENTRIES) {
            communicationLog.removeAt(0)
        }

        Log.d(TAG, message)
    }
}