package com.blitztech.pudokiosk.deviceio.rs485

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

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

    /**
     * Initialize and connect to the RS232-to-USB converter
     * @return true if connection successful
     */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        if (isConnected) {
            Log.d(TAG, "Already connected to RS232-to-USB converter")
            return@withContext true
        }

        try {
            Log.d(TAG, "Connecting to RS232-to-USB converter (VID:04E2 PID:1414)...")

            val usbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

            // Find our specific RS232-to-USB converter device
            val targetDriver = availableDrivers.find { driver ->
                val dev = driver.device
                dev.vendorId == TARGET_VID && dev.productId == TARGET_PID
            }

            if (targetDriver == null) {
                Log.e(TAG, "RS232-to-USB converter not found (VID:04E2 PID:1414)")
                Log.d(TAG, "Available devices: ${availableDrivers.map {
                    "VID:${String.format("%04X", it.device.vendorId)} PID:${String.format("%04X", it.device.productId)}"
                }}")
                return@withContext false
            }

            // Ensure it's a CDC device with multiple ports
            if (targetDriver !is CdcAcmSerialDriver) {
                Log.e(TAG, "Device is not a CDC-ACM device, got: ${targetDriver::class.simpleName}")
                return@withContext false
            }

            val ports = targetDriver.ports
            if (ports.size < TARGET_PORT + 1) {
                Log.e(TAG, "Device doesn't have port $TARGET_PORT (has ${ports.size} ports)")
                return@withContext false
            }

            // Get our specific working port (Port 2)
            val targetPort = ports[TARGET_PORT]
            device = targetDriver.device

            // Close if already open to ensure clean state
            if (targetPort.isOpen) {
                try {
                    targetPort.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing already open port: ${e.message}")
                }
                delay(CONNECTION_RETRY_DELAY_MS)
            }

            // Request USB permission if needed
            if (!usbManager.hasPermission(device)) {
                Log.e(TAG, "No USB permission for RS232-to-USB converter")
                return@withContext false
            }

            // Open the connection with proper error handling
            val connection = usbManager.openDevice(device)
            if (connection == null) {
                Log.e(TAG, "Failed to open USB connection to RS232-to-USB converter")
                return@withContext false
            }

            // Open and configure the serial port
            targetPort.open(connection)
            delay(100) // Allow port to stabilize

            // Configure communication parameters to match STM32
            targetPort.setParameters(BAUD_RATE, DATA_BITS, STOP_BITS, PARITY)

            // Set DTR and RTS for proper CDC-ACM operation
            try {
                targetPort.dtr = true
                targetPort.rts = false
                delay(50)
            } catch (e: Exception) {
                Log.w(TAG, "Could not set DTR/RTS: ${e.message}")
                // Continue anyway, might still work
            }

            port = targetPort
            isConnected = true

            Log.i(TAG, "✅ Connected to RS232-to-USB converter on port $TARGET_PORT")
            Log.d(TAG, "Device info: ${getDeviceInfo()}")
            return@withContext true

        } catch (e: IOException) {
            Log.e(TAG, "IOException during connection: ${e.message}", e)
            cleanup()
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during connection: ${e.message}", e)
            cleanup()
            return@withContext false
        }
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
     * Get device information string
     */
    fun getDeviceInfo(): String {
        return if (device != null) {
            "RS232-to-USB VID:${String.format("%04X", device!!.vendorId)} " +
                    "PID:${String.format("%04X", device!!.productId)} Port:$TARGET_PORT"
        } else {
            "No device"
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