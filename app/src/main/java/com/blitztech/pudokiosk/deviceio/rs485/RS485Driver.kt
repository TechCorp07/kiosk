package com.blitztech.pudokiosk.deviceio.rs485

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
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
    private val logMessages = mutableListOf<String>()
    private var currentDevice: UsbDevice? = null

    companion object {
        private const val TAG = "RS485Driver"
        private const val MAX_LOG_ENTRIES = 50
        private const val BAUD_RATE = 9600
    }

    /**
     * Test continuous listening for any incoming data
     */
    suspend fun startListening(durationMs: Long = 10000): List<ByteArray> = withContext(Dispatchers.IO) {
        val p = port ?: run {
            log("❌ No connection - please connect to a device first")
            return@withContext emptyList()
        }

        val receivedData = mutableListOf<ByteArray>()
        val startTime = System.currentTimeMillis()

        log("👂 Listening for incoming data (${durationMs / 1000}s)...")

        try {
            while (System.currentTimeMillis() - startTime < durationMs) {
                val buffer = ByteArray(256)
                val bytesRead = p.read(buffer, 100) // Short timeout for polling

                if (bytesRead > 0) {
                    val data = buffer.copyOf(bytesRead)
                    receivedData.add(data)
                    log("📥 Received: ${toHexString(data)}")
                }

                delay(10) // Small delay to prevent tight loop
            }
        } catch (e: Exception) {
            log("❌ Error during listening: ${e.message}")
        }

        log("👂 Listening completed. Received ${receivedData.size} messages")
        receivedData
    }

    /**
     * Connect to a specific serial device
     */
    suspend fun connect(
        baudRate: Int = BAUD_RATE,
        portNumber: Int = 0
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            disconnect()

            val usbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager

            // Find the hardcoded USB device (VID:1250, PID:5140)
            val deviceList = usbManager.deviceList
            var targetDevice: UsbDevice? = null

            for (device in deviceList.values) {
                if (device.vendorId == 1250 && device.productId == 5140) {
                    targetDevice = device
                    break
                }
            }

            if (targetDevice == null) {
                log("❌ Hardcoded device (VID:1250, PID:5140) not found")
                return@withContext false
            }

            // Probe the device to get the driver
            val driver = UsbSerialProber.getDefaultProber().probeDevice(targetDevice)
            if (driver == null) {
                log("❌ No suitable driver found for device")
                return@withContext false
            }

            val connection = usbManager.openDevice(targetDevice)

            if (connection == null) {
                log("❌ Failed to open device - permission denied")
                return@withContext false
            }

            // Select the specific port
            val availablePorts = driver.ports
            if (portNumber >= availablePorts.size) {
                log("❌ Port $portNumber not available (device has ${availablePorts.size} ports)")
                connection.close()
                return@withContext false
            }

            val selectedPort = availablePorts[portNumber]
            log("📡 Using port: ${selectedPort.javaClass.simpleName} (Port ${portNumber + 1})")

            try {
                selectedPort.close()
                delay(150) // Give time to close
                log("🔧 Closed existing port connection")
            } catch (e: Exception) {
                // Ignore - port probably wasn't open
            }
            try {
                selectedPort.open(connection)
                log("✅ Port opened successfully")
            } catch (e: IOException) {
                if (e.message?.contains("Already open") == true) {
                    log("⚠️ Port already open - will try to configure existing connection")
                } else {
                    log("❌ Failed to open port: ${e.message}")
                    connection.close()
                    return@withContext false
                }
            }

            var configSuccess = false
            try {
                selectedPort.dtr = true
                selectedPort.rts = false  // Some devices prefer RTS low
                configSuccess = true
            } catch (e: Exception) {
                log("⚠️ Minimal config failed: ${e.message ?: "null"}")
            }

            if (configSuccess) {
                port = selectedPort
                currentDevice = targetDevice

                log("✅ Connected successfully to Port ${portNumber + 1}!")
                log("📡 Device: ${targetDevice.deviceName}")
                log("🔧 Driver: ${selectedPort.javaClass.simpleName}")
                log("🎯 Ready for communication testing")

                delay(100) // Allow port to stabilize
                return@withContext true
            } else {
                log("❌ All configuration methods failed")
                try {
                    selectedPort.close()
                    connection.close()
                } catch (closeEx: Exception) {
                    // Ignore cleanup errors
                }
                return@withContext false
            }

            return@withContext false

        } catch (e: Exception) {
            log("❌ Connection error: ${e.message}")
            Log.e(RS485Driver.Companion.TAG, "Error connecting to device", e)
            return@withContext false
        }
    }

    /**
     * Internal logging
     */
    private fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message"

        logMessages.add(logEntry)
        if (logMessages.size > RS485Driver.Companion.MAX_LOG_ENTRIES) {
            logMessages.removeAt(0)
        }

        Log.d(RS485Driver.Companion.TAG, message)
    }

    /**
     * Convert byte array to hex string
     */
    private fun toHexString(data: ByteArray): String {
        return data.joinToString(" ") { "%02X".format(it) }
    }

    /**
     * Get connection status
     */
    fun isConnected(): Boolean = port != null && currentDevice != null

    /**
     * Disconnect from current device
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            port?.close()
            log("🔌 Disconnected")
        } catch (e: Exception) {
            log("⚠️ Error during disconnect: ${e.message}")
        } finally {
            port = null
            currentDevice = null
        }
    }

    /**
     * Send data to the connected device
     */
    suspend fun sendData(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val p = port ?: run {
            log("❌ Cannot send - no connection")
            return@withContext false
        }

        if (data.isEmpty()) {
            log("⚠️ Cannot send empty data")
            return@withContext false
        }

        try {
            log("📤 Sending ${data.size} bytes: ${toHexString(data)}")

            // Send data - throws exception on failure
            p.write(data, 1000) // 1 second timeout

            // Small delay to ensure data is transmitted
            delay(10)

            log("✅ Send successful (${data.size} bytes)")
            return@withContext true

        } catch (e: java.io.IOException) {
            log("❌ I/O error during send: ${e.message}")
            Log.e(TAG, "I/O error sending data", e)
            return@withContext false
        } catch (e: Exception) {
            log("❌ Send error: ${e.message}")
            Log.e(TAG, "Error sending data", e)
            return@withContext false
        }
    }

    /**
     * Receive data from the connected device
     */
    suspend fun receiveData(timeoutMs: Int = 1000): ByteArray = withContext(Dispatchers.IO) {
        val p = port ?: run {
            log("❌ Cannot receive - no connection")
            return@withContext ByteArray(0)
        }

        try {
            val buffer = ByteArray(256)
            val bytesRead = p.read(buffer, timeoutMs)

            if (bytesRead > 0) {
                val data = buffer.copyOf(bytesRead)
                log("📥 Received ${bytesRead} bytes: ${toHexString(data)}")
                return@withContext data
            } else {
                // No data received (timeout or no data available)
                return@withContext ByteArray(0)
            }

        } catch (e: java.io.IOException) {
            // Timeout exceptions are normal when no data is available
            if (e.message?.contains("timeout", ignoreCase = true) != true) {
                log("❌ I/O error during receive: ${e.message}")
                Log.e(TAG, "I/O error receiving data", e)
            }
            return@withContext ByteArray(0)
        } catch (e: Exception) {
            if (e.message?.contains("timeout", ignoreCase = true) != true) {
                log("❌ Receive error: ${e.message}")
                Log.e(TAG, "Error receiving data", e)
            }
            return@withContext ByteArray(0)
        }
    }

    /**
     * Clear any pending data in the receive buffer
     */
    suspend fun clearReceiveBuffer() = withContext(Dispatchers.IO) {
        val p = port ?: return@withContext

        try {
            // Read and discard any pending data
            val buffer = ByteArray(256)
            var totalCleared = 0

            // Keep reading until no more data (with very short timeout)
            var attempts = 0
            while (attempts < 10) { // Prevent infinite loop
                try {
                    val bytesRead = p.read(buffer, 50) // Very short timeout
                    if (bytesRead <= 0) break
                    totalCleared += bytesRead
                    attempts++
                } catch (e: Exception) {
                    // Timeout or no more data - exit loop
                    break
                }
            }

            if (totalCleared > 0) {
                log("🧹 Cleared $totalCleared bytes from receive buffer")
            }

        } catch (e: Exception) {
            // Ignore most exceptions when clearing buffer
            if (e.message?.contains("timeout", ignoreCase = true) != true) {
                Log.w(TAG, "Warning during buffer clear: ${e.message}")
            }
        }
    }

    /**
     * Send command and wait for response (convenience method)
     */
    suspend fun sendCommandAndReceive(
        command: ByteArray,
        responseTimeoutMs: Int = 2000
    ): ByteArray = withContext(Dispatchers.IO) {

        if (command.isEmpty()) {
            log("❌ Cannot send empty command")
            return@withContext ByteArray(0)
        }

        // Clear any pending data first
        clearReceiveBuffer()

        // Send command
        val sendSuccess = sendData(command)
        if (!sendSuccess) {
            log("❌ Failed to send command")
            return@withContext ByteArray(0)
        }

        // Wait for response
        val startTime = System.currentTimeMillis()
        val responseData = mutableListOf<Byte>()

        while (System.currentTimeMillis() - startTime < responseTimeoutMs) {
            val data = receiveData(100) // Short timeout for polling

            if (data.isNotEmpty()) {
                responseData.addAll(data.toList())

                // Check if we have a complete Winnsen frame (7 bytes starting with 0x90)
                if (responseData.size >= 7) {
                    val frame = responseData.toByteArray()
                    // Look for valid frame in the received data
                    for (i in 0..frame.size - 7) {
                        if (frame[i] == 0x90.toByte() &&
                            frame.size >= i + 7 &&
                            frame[i + 6] == 0x03.toByte() &&
                            frame[i + 1] == 0x07.toByte()) { // Check length byte
                            // Found complete valid frame
                            val completeFrame = frame.sliceArray(i until i + 7)
                            log("✅ Received complete response frame: ${toHexString(completeFrame)}")
                            return@withContext completeFrame
                        }
                    }
                }
            }

            delay(10) // Small delay to prevent tight loop
        }

        if (responseData.isNotEmpty()) {
            log("⚠️ Received partial response: ${responseData.size} bytes - ${toHexString(responseData.toByteArray())}")
            return@withContext responseData.toByteArray()
        } else {
            log("❌ No response received within ${responseTimeoutMs}ms")
            return@withContext ByteArray(0)
        }
    }

    /**
     * Test basic communication by sending a simple command
     */
    suspend fun testBasicCommunication(): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            log("❌ Not connected")
            return@withContext false
        }

        log("🧪 Testing basic communication...")

        // Send a status check command for lock 1
        val testCommand = byteArrayOf(
            0x90.toByte(), // Header
            0x06.toByte(), // Length
            0x12.toByte(), // Status function
            0x00.toByte(), // Station 0
            0x01.toByte(), // Lock 1
            0x03.toByte()  // End
        )

        try {
            val response = sendCommandAndReceive(testCommand, 3000)

            if (response.size == 7 &&
                response[0] == 0x90.toByte() &&
                response[1] == 0x07.toByte() &&
                response[6] == 0x03.toByte()) {
                log("✅ Communication test successful!")
                log("📋 Response: ${toHexString(response)}")
                return@withContext true
            } else if (response.isNotEmpty()) {
                log("❌ Invalid response format: ${toHexString(response)}")
                return@withContext false
            } else {
                log("❌ No response received")
                return@withContext false
            }

        } catch (e: Exception) {
            log("❌ Communication test failed: ${e.message}")
            Log.e(TAG, "Communication test error", e)
            return@withContext false
        }
    }

    /**
     * Get formatted log messages
     */
    fun getLogMessages(): List<String> = logMessages.toList()

    /**
     * Clear log messages
     */
    fun clearLog() {
        logMessages.clear()
    }
}