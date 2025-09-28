package com.blitztech.pudokiosk.deviceio.rs485

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fixed RS485 Driver for STM32L412 Locker Controller
 *
 * System Architecture:
 * Android Box → RS232 → RS485 Converter → MAX3485 → STM32L412
 *
 * Fixed Configuration:
 * - Device: VID:04E2 PID:1414 (RS232-to-USB converter, NOT the STM32)
 * - Port: 2 (of 4 available - working port as confirmed by testing)
 * - Baud: 9600, 8N1
 * - Protocol: Winnsen Smart Locker
 * - Timeout: 800ms for read operations
 */
class RS485Driver(private val ctx: Context) {

    private var port: UsbSerialPort? = null
    private var device: UsbDevice? = null
    private var isConnected = false

    companion object {
        private const val TAG = "RS485Driver"

        // Fixed device configuration from testing (RS232-to-USB converter)
        private const val TARGET_VID = 0x04E2
        private const val TARGET_PID = 0x1414
        private const val TARGET_PORT = 2  // Port 2 confirmed working

        // Communication settings (match STM32 configuration)
        private const val BAUD_RATE = 9600
        private const val DATA_BITS = 8
        private const val STOP_BITS = UsbSerialPort.STOPBITS_1
        private const val PARITY = UsbSerialPort.PARITY_NONE

        // Timing constants optimized for STM32L412
        private const val CONNECT_TIMEOUT_MS = 2000
        private const val READ_TIMEOUT_MS = 800
        private const val WRITE_DELAY_MS = 50L
        private const val CONNECTION_RETRY_DELAY_MS = 200L
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR,
        PERMISSION_DENIED
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private var autoReconnectEnabled = true
    /**
     * Initialize and connect to the RS232-to-USB converter
     * @return true if connection successful
     */
    /**
     * Auto-connect to STM32L412 RS485 device - no scanning required
     * @return true if connection successful
     */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        if (isConnected && port != null) {
            Log.d(TAG, "Already connected to STM32L412")
            return@withContext true
        }

        _connectionState.value = ConnectionState.CONNECTING
        Log.d(TAG, "Auto-connecting to STM32L412 (VID:04E2 PID:1414 Port:2)...")

        try {
            val usbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

            // Find our specific RS232-to-USB converter device
            val targetDriver = availableDrivers.find { driver ->
                val dev = driver.device
                dev.vendorId == TARGET_VID && dev.productId == TARGET_PID
            }

            if (targetDriver == null) {
                Log.w(TAG, "STM32L412 RS232-to-USB converter not found")
                _connectionState.value = ConnectionState.ERROR
                scheduleReconnect()
                return@withContext false
            }

            device = targetDriver.device
            Log.d(TAG, "Found STM32L412 converter: ${device?.deviceName}")

            // Check USB permission
            if (!usbManager.hasPermission(device)) {
                Log.d(TAG, "Requesting USB permission for STM32L412...")
                // In production, you might want to handle permission requests
                // For now, we'll treat this as an error and retry
                _connectionState.value = ConnectionState.PERMISSION_DENIED
                scheduleReconnect()
                return@withContext false
            }

            // Get the specific port (Port 2 confirmed working)
            if (targetDriver.ports.size <= TARGET_PORT) {
                Log.e(TAG, "Port $TARGET_PORT not available (only ${targetDriver.ports.size} ports)")
                _connectionState.value = ConnectionState.ERROR
                scheduleReconnect()
                return@withContext false
            }

            port = targetDriver.ports[TARGET_PORT]
            Log.d(TAG, "Using Port $TARGET_PORT for STM32L412 communication")

            // Open the connection
            val connection = usbManager.openDevice(device)
            if (connection == null) {
                Log.e(TAG, "Failed to open USB device connection")
                _connectionState.value = ConnectionState.ERROR
                scheduleReconnect()
                return@withContext false
            }

            port?.open(connection)
            port?.setParameters(
                BAUD_RATE,
                DATA_BITS,
                STOP_BITS,
                PARITY
            )

            isConnected = true
            reconnectAttempts = 0
            _connectionState.value = ConnectionState.CONNECTED

            Log.i(TAG, "✅ STM32L412 connected successfully via Port $TARGET_PORT")
            Log.d(TAG, "🔧 Settings: 9600 baud, 8N1, Timeout: ${READ_TIMEOUT_MS}ms")

            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "STM32L412 connection failed: ${e.message}", e)
            _connectionState.value = ConnectionState.ERROR
            scheduleReconnect()
            return@withContext false
        }
    }

    /**
     * Schedule automatic reconnection attempt
     */
    private fun scheduleReconnect() {
        if (!autoReconnectEnabled || reconnectAttempts >= maxReconnectAttempts) {
            if (reconnectAttempts >= maxReconnectAttempts) {
                Log.w(TAG, "Max reconnection attempts ($maxReconnectAttempts) reached for STM32L412")
            }
            return
        }

        reconnectAttempts++
        val delayMs = (reconnectAttempts * CONNECTION_RETRY_DELAY_MS).coerceAtMost(5000L)

        Log.d(TAG, "Scheduling STM32L412 reconnection attempt $reconnectAttempts in ${delayMs}ms")

        scope.launch {
            delay(delayMs)
            if (!isConnected && autoReconnectEnabled) {
                Log.d(TAG, "Attempting STM32L412 reconnection $reconnectAttempts/$maxReconnectAttempts")
                connect()
            }
        }
    }

    /**
     * Enable/disable automatic reconnection
     */
    fun setAutoReconnect(enabled: Boolean) {
        autoReconnectEnabled = enabled
        Log.d(TAG, "STM32L412 auto-reconnect: ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Force manual reconnection attempt
     */
    suspend fun reconnect(): Boolean {
        Log.i(TAG, "Manual STM32L412 reconnection requested")
        reconnectAttempts = 0
        disconnect()
        delay(100) // Brief pause before reconnection
        return connect()
    }

    /**
     * Send command and read response with corrected API usage
     * @param command Command bytes to send
     * @param expectedResponseSize Expected response length (7 bytes for Winnsen protocol)
     * @param timeoutMs Read timeout in milliseconds
     * @return Response bytes or empty array on failure
     */
    suspend fun writeRead(
        command: ByteArray,
        expectedResponseSize: Int,
        timeoutMs: Int = READ_TIMEOUT_MS
    ): ByteArray = withContext(Dispatchers.IO) {
        if (!isConnected || port == null) {
            Log.e(TAG, "Not connected to RS232-to-USB converter")
            return@withContext ByteArray(0)
        }

        try {
            val currentPort = port!!

            // Clear any pending data with correct API
            try {
                val clearBuffer = ByteArray(64)
                currentPort.read(clearBuffer, 10) // Short timeout for clearing
            } catch (e: Exception) {
                // Ignore timeout on clear operation
            }

            // Send command
            Log.d(TAG, "Sending: ${command.joinToString(" ") { "0x%02X".format(it) }}")

            currentPort.write(command, timeoutMs)
            Log.d(TAG, "Command sent successfully")

            delay(WRITE_DELAY_MS) // Allow STM32 to process

            // Read response with correct API usage
            val response = ByteArray(expectedResponseSize)
            var totalRead = 0
            val startTime = System.currentTimeMillis()

            while (totalRead < expectedResponseSize &&
                (System.currentTimeMillis() - startTime) < timeoutMs) {

                try {
                    // Read remaining bytes needed
                    val remainingBytes = expectedResponseSize - totalRead
                    val tempBuffer = ByteArray(remainingBytes)

                    val bytesRead = currentPort.read(tempBuffer, 100)

                    if (bytesRead > 0) {
                        // Copy read bytes to response buffer at correct offset
                        System.arraycopy(tempBuffer, 0, response, totalRead, bytesRead)
                        totalRead += bytesRead
                    } else {
                        delay(10) // Short delay if no data available
                    }
                } catch (e: IOException) {
                    if (totalRead == 0) {
                        Log.w(TAG, "No response received within timeout")
                        break
                    } else {
                        // Partial response received, continue waiting
                        delay(10)
                    }
                }
            }

            if (totalRead == expectedResponseSize) {
                Log.d(TAG, "Received: ${response.joinToString(" ") { "0x%02X".format(it) }}")
                return@withContext response
            } else {
                Log.w(TAG, "Incomplete response: $totalRead/$expectedResponseSize bytes")
                if (totalRead > 0) {
                    Log.d(TAG, "Partial data: ${response.sliceArray(0 until totalRead).joinToString(" ") { "0x%02X".format(it) }}")
                }
                return@withContext ByteArray(0)
            }

        } catch (e: IOException) {
            Log.e(TAG, "Communication error: ${e.message}", e)
            return@withContext ByteArray(0)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during communication: ${e.message}", e)
            return@withContext ByteArray(0)
        }
    }

    /**
     * Read raw data from the port (for listening)
     */
    suspend fun readRawData(timeoutMs: Int = 100): ByteArray = withContext(Dispatchers.IO) {
        val p = port ?: return@withContext ByteArray(0)

        try {
            val buffer = ByteArray(256)
            val bytesRead = p.read(buffer, timeoutMs)

            return@withContext if (bytesRead > 0) {
                buffer.copyOf(bytesRead)
            } else {
                ByteArray(0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading raw data: ${e.message}")
            return@withContext ByteArray(0)
        }
    }

    /**
     * Test connection by sending a simple status command
     * @return true if communication test successful
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected) {
            return@withContext false
        }

        try {
            // Send status command for lock 1 as a connectivity test
            val testCommand = byteArrayOf(0x90.toByte(), 0x06, 0x12, 0x00, 0x01, 0x03)
            val response = writeRead(testCommand, 7, 500)

            val isValid = response.size == 7 &&
                    response[0] == 0x90.toByte() &&
                    response[6] == 0x03.toByte()

            Log.d(TAG, "Connection test: ${if (isValid) "PASS" else "FAIL"}")
            return@withContext isValid

        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Initialize STM32L412 connection on app startup
     * Call this from Application.onCreate() or main activity
     */
    fun initializeOnStartup(context: Context) {
        Log.i(TAG, "Initializing STM32L412 on app startup...")

        scope.launch {
            // Small delay to let USB system stabilize
            delay(1000)
            connect()
        }
    }

    /**
     * Disconnect from the RS232-to-USB converter
     */
    suspend fun disconnect(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            cleanup()
            Log.i(TAG, "Disconnected from RS232-to-USB converter")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect: ${e.message}", e)
            false
        }
    }

    /**
     * Check if currently connected
     */
    fun isConnected(): Boolean = isConnected

    /**
     * Get current device connection information
     */
    fun getDeviceInfo(): String {
        return when (_connectionState.value) {
            ConnectionState.CONNECTED -> {
                "STM32L412 via ${device?.deviceName ?: "Unknown"} Port:$TARGET_PORT (VID:04E2 PID:1414) @ 9600 baud"
            }
            ConnectionState.CONNECTING -> "Connecting to STM32L412..."
            ConnectionState.PERMISSION_DENIED -> "USB permission denied"
            ConnectionState.ERROR -> "Connection error"
            ConnectionState.DISCONNECTED -> "Disconnected"
        }
    }

    /**
     * Clean up resources
     */
    private fun cleanup() {
        isConnected = false
        port?.let { p ->
            try {
                if (p.isOpen) {
                    p.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error closing port: ${e.message}")
            }
        }
        port = null
        device = null
    }
}