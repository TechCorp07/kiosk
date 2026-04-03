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
        private const val TX_RX_TURNAROUND_MS = 15L   // RS485 direction switch settling time
        // STM32 responds AFTER the 750ms lock pulse + 50ms settling + transmission overhead
        private const val UNLOCK_RESPONSE_TIMEOUT_MS = 2000L
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
                log("✅ Connected to locker controller")
            } else {
                log("❌ Connection failed")
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

        val unlockCommand = WinnsenProtocol.createUnlockCommand(lockNumber)
            ?: return@withContext WinnsenProtocol.LockOperationResult(
                success = false,
                lockNumber = lockNumber,
                status = WinnsenProtocol.LockStatus.UNKNOWN,
                errorMessage = "Failed to create unlock command"
            )

        log("\uD83D\uDD13 Unlocking lock $lockNumber...")
        log("\uD83D\uDCE4 Sending: ${unlockCommand.toHexString()}")

        // The STM32 now pulses the lock FIRST, then responds with the actual
        // post-unlock status. We send the unlock command and wait for this
        // single response. Retry only if no response or status ≠ OPEN.
        repeat(COMMAND_RETRY_COUNT) { attempt ->
            val startTime = System.currentTimeMillis()
            val result = sendCommand(unlockCommand, WinnsenProtocol.CommandType.UNLOCK, lockNumber)
            val elapsed = System.currentTimeMillis() - startTime

            if (result.success && result.status == WinnsenProtocol.LockStatus.OPEN) {
                log("✅ Lock $lockNumber confirmed open (${elapsed}ms)")
                return@withContext WinnsenProtocol.LockOperationResult(
                    success = true,
                    lockNumber = lockNumber,
                    status = WinnsenProtocol.LockStatus.OPEN,
                    responseTime = elapsed
                )
            }

            // Cooldown rejection (0x02): STM32 duty-cycle protection is active.
            // Do NOT retry — the lock won't unlock until ~15s cooldown expires.
            if (result.success && result.status == WinnsenProtocol.LockStatus.COOLDOWN) {
                log("⏳ Lock $lockNumber is in cooldown (duty-cycle protection). Try again in ~15s.")
                return@withContext WinnsenProtocol.LockOperationResult(
                    success = false,
                    lockNumber = lockNumber,
                    status = WinnsenProtocol.LockStatus.COOLDOWN,
                    errorMessage = "Lock is in cooldown (duty-cycle protection). Try again in ~15 seconds.",
                    responseTime = elapsed
                )
            }

            // Response received but lock not open, or no response at all
            val reason = result.errorMessage ?: "status: ${result.status.displayName}"
            log("⚠️ Unlock attempt ${attempt + 1}/$COMMAND_RETRY_COUNT failed — $reason")

            if (attempt < COMMAND_RETRY_COUNT - 1) {
                delay(RETRY_DELAY_MS)
            }
        }

        log("❌ Lock $lockNumber failed to open after $COMMAND_RETRY_COUNT attempts")
        return@withContext WinnsenProtocol.LockOperationResult(
            success = false,
            lockNumber = lockNumber,
            status = WinnsenProtocol.LockStatus.CLOSED,
            errorMessage = "Lock did not open after $COMMAND_RETRY_COUNT attempts"
        )
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

        // RS485 turnaround delay: wait for XR21V1414 to finish TX and switch direction to RX
        delay(TX_RX_TURNAROUND_MS)

        // Wait for response — the expected station comes from the command frame
        val expectedStation = command.station
        val response = waitForResponse(expectedType, lockNumber, expectedStation)

        if (response == null) {
            return WinnsenProtocol.LockOperationResult(
                success = false,
                lockNumber = lockNumber,
                status = WinnsenProtocol.LockStatus.UNKNOWN,
                errorMessage = "No response received within timeout"
            )
        }

        log("\uD83D\uDCE5 Received: ${response.toHexString()}")

        // The STM32 now responds AFTER the lock pulse completes, so the
        // status byte in the response is the actual physical state.
        val lockStatus = WinnsenProtocol.LockStatus.fromByte(response.status)
        log("\uD83D\uDD12 Lock $lockNumber ${expectedType.displayName.lowercase()} — Status: ${lockStatus.displayName}")

        return WinnsenProtocol.LockOperationResult(
            success = true,
            lockNumber = lockNumber,
            status = lockStatus
        )
    }

    /**
     * Wait for a specific response frame, accumulating fragments across multiple reads.
     * USB serial drivers often deliver a 7-byte frame as multiple small chunks (e.g. 1+1+5).
     * We accumulate all bytes and scan for a complete Winnsen frame each time new data arrives.
     */
    private suspend fun waitForResponse(
        expectedType: WinnsenProtocol.CommandType,
        expectedLockNumber: Int,
        expectedStation: Byte
    ): WinnsenProtocol.ResponseFrame? {

        val startTime = System.currentTimeMillis()
        val accumulator = mutableListOf<Byte>()

        // Use a longer timeout for unlock commands (STM32 responds after 2s pulse)
        val timeoutMs = if (expectedType == WinnsenProtocol.CommandType.UNLOCK) {
            UNLOCK_RESPONSE_TIMEOUT_MS
        } else {
            WinnsenProtocol.RESPONSE_TIMEOUT_MS
        }

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val chunk = rs485Driver.receiveData(100)

            if (chunk.isNotEmpty()) {
                accumulator.addAll(chunk.toList())

                if (accumulator.size > 32) {
                    accumulator.subList(0, accumulator.size - 32).clear()
                }

                val buf = accumulator.toByteArray()
                for (i in buf.indices) {
                    if (buf[i] == 0x90.toByte() && i + 6 < buf.size && buf[i + 6] == 0x03.toByte()) {
                        val candidate = buf.copyOfRange(i, i + 7)
                        val frame = WinnsenProtocol.parseIncomingFrame(candidate)
                        if (frame != null) {
                            val expectedSlot = WinnsenProtocol.lockByteFor(expectedLockNumber)
                            // Match on function + lock slot + station to ensure
                            // we accept only the response from the correct board
                            if (frame.function == expectedType.responseCode &&
                                frame.lockNumber == expectedSlot &&
                                frame.station == expectedStation) {
                                return frame
                            }
                        }
                    }
                }
            }

            delay(10)
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