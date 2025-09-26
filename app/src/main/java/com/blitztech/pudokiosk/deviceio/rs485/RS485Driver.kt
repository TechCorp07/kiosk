package com.blitztech.pudokiosk.deviceio.rs485

import android.content.Context
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
 * Production RS485 Driver for STM32L412 Locker Controller
 *
 * Fixed Configuration:
 * - Device: VID:04E2 PID:1414 (CDC device)
 * - Port: 2 (of 4 available)
 * - Baud: 9600, 8N1
 * - Protocol: Winnsen Smart Locker
 */
class RS485Driver(private val ctx: Context) {

    private var port: UsbSerialPort? = null
    private var isConnected = false

    companion object {
        private const val TAG = "RS485Driver"

        // Fixed device configuration
        private const val TARGET_VID = 0x04E2
        private const val TARGET_PID = 0x1414
        private const val TARGET_PORT = 2  // Use port 2 of the CDC device

        // Communication settings
        private const val BAUD_RATE = 9600
        private const val DATA_BITS = 8
        private const val STOP_BITS = UsbSerialPort.STOPBITS_1
        private const val PARITY = UsbSerialPort.PARITY_NONE

        // Timing constants
        private const val CONNECT_TIMEOUT_MS = 2000
        private const val READ_TIMEOUT_MS = 800
        private const val WRITE_DELAY_MS = 50L
    }

    /**
     * Initialize and connect to the STM32L412 board
     * @return true if connection successful
     */
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        if (isConnected) {
            Log.d(TAG, "Already connected")
            return@withContext true
        }

        try {
            Log.d(TAG, "Connecting to STM32L412 board (VID:04E2 PID:1414)...")

            val usbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

            // Find our specific device
            val targetDriver = availableDrivers.find { driver ->
                val device = driver.device
                device.vendorId == TARGET_VID && device.productId == TARGET_PID
            }

            if (targetDriver == null) {
                Log.e(TAG, "STM32L412 board not found (VID:04E2 PID:1414)")
                return@withContext false
            }

            // Ensure it's a CDC device with multiple ports
            if (targetDriver !is CdcAcmSerialDriver) {
                Log.e(TAG, "Device is not a CDC device")
                return@withContext false
            }

            val ports = targetDriver.ports
            if (ports.size < TARGET_PORT + 1) {
                Log.e(TAG, "Device doesn't have port $TARGET_PORT (has ${ports.size} ports)")
                return@withContext false
            }

            // Get our specific port
            val targetPort = ports[TARGET_PORT]

            // Close if already open
            if (targetPort.isOpen) {
                targetPort.close()
                delay(150)
            }

            // Open and configure
            //targetPort.open(usbManager.getConnection(targetDriver.device))
            targetPort.setParameters(BAUD_RATE, DATA_BITS, STOP_BITS, PARITY)

            port = targetPort
            isConnected = true

            Log.i(TAG, "✅ Connected to STM32L412 on port $TARGET_PORT")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}", e)
            cleanup()
            return@withContext false
        }
    }

    /**
     * Send command and read response
     * @param command Command bytes to send
     * @param expectedResponseSize Expected response length
     * @return Response bytes or empty array on failure
     */
    suspend fun writeRead(
        command: ByteArray,
        expectedResponseSize: Int,
        timeoutMs: Int = READ_TIMEOUT_MS
    ): ByteArray = withContext(Dispatchers.IO) {
        if (!isConnected || port == null) {
            Log.e(TAG, "Not connected")
            return@withContext ByteArray(0)
        }

        try {
            val currentPort = port!!

            // Send command
            Log.d(TAG, "Sending: ${toHexString(command)}")
            currentPort.write(command, timeoutMs)
            delay(WRITE_DELAY_MS)

            // Read response
            val response = ByteArray(expectedResponseSize)
            val bytesRead = currentPort.read(response, timeoutMs)

            if (bytesRead != expectedResponseSize) {
                Log.w(TAG, "Expected $expectedResponseSize bytes, got $bytesRead")
                return@withContext ByteArray(0)
            }

            Log.d(TAG, "Received: ${toHexString(response)}")
            return@withContext response

        } catch (e: IOException) {
            Log.e(TAG, "Communication error: ${e.message}")
            return@withContext ByteArray(0)
        }
    }

    /**
     * Send command without expecting response
     * @param command Command bytes to send
     * @return true if sent successfully
     */
    suspend fun write(command: ByteArray, timeoutMs: Int = READ_TIMEOUT_MS): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected || port == null) {
            Log.e(TAG, "Not connected")
            return@withContext false
        }

        try {
            Log.d(TAG, "Sending: ${toHexString(command)}")
            port!!.write(command, timeoutMs)
            return@withContext true

        } catch (e: IOException) {
            Log.e(TAG, "Write error: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Check if driver is connected
     */
    fun isConnected(): Boolean = isConnected && port != null

    /**
     * Disconnect from device
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Disconnecting...")
        cleanup()
    }

    /**
     * Get device connection info
     */
    fun getDeviceInfo(): String? {
        return if (isConnected) {
            "STM32L412 Board (VID:04E2 PID:1414) Port:$TARGET_PORT"
        } else {
            null
        }
    }

    /**
     * Test basic communication
     * @return true if device responds
     */
    suspend fun testCommunication(): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected) {
            return@withContext false
        }

        try {
            // Send a basic status command to lock 1
            val testCommand = WinnsenProtocol.createStatusCommand(0, 1)
            val response = writeRead(testCommand, 7)

            return@withContext response.isNotEmpty() && WinnsenProtocol.validateResponse(testCommand, response)

        } catch (e: Exception) {
            Log.e(TAG, "Communication test failed: ${e.message}")
            return@withContext false
        }
    }

    private fun cleanup() {
        try {
            port?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing port: ${e.message}")
        } finally {
            port = null
            isConnected = false
            Log.d(TAG, "Disconnected")
        }
    }

    private fun toHexString(data: ByteArray): String {
        return data.joinToString(" ") { "%02X".format(it) }
    }
}