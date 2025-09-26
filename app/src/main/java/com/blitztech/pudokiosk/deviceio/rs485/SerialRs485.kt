package com.blitztech.pudokiosk.deviceio.rs485

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Production RS485 Serial Driver for STM32L412 Locker Controller
 *
 * Fixed Configuration:
 * - VID: 04E2, PID: 1414 (CDC-ACM device)
 * - Port: 2 (fixed)
 * - Baud: 9600, 8N1
 * - Station: 0 (hardcoded)
 *
 * Hardware: STM32L412 + MAX485 IC via RS485 to RS232/USB converter
 */
class SerialRs485(private val context: Context) {

    companion object {
        private const val TAG = "SerialRs485"

        // Fixed hardware configuration
        private const val TARGET_VID = 0x04E2
        private const val TARGET_PID = 0x1414
        private const val TARGET_PORT_INDEX = 2
        private const val BAUD_RATE = 9600

        // Communication settings
        private const val DATA_BITS = 8
        private const val STOP_BITS = UsbSerialPort.STOPBITS_1
        private const val PARITY = UsbSerialPort.PARITY_NONE
        private const val READ_TIMEOUT_MS = 500
        private const val WRITE_TIMEOUT_MS = 500
        private const val CONNECTION_DELAY_MS = 150L
    }

    private var serialPort: UsbSerialPort? = null
    private var isConnected = false

    /**
     * Initialize and connect to the fixed CDC device
     * @return true if connection successful
     */
    suspend fun open(baud: Int = BAUD_RATE): Boolean {
        try {
            if (isConnected) {
                Log.d(TAG, "Already connected")
                return true
            }

            Log.d(TAG, "Connecting to STM32L412 locker controller...")

            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

            // Find our specific CDC device
            val targetDriver = availableDrivers.find { driver ->
                val device = driver.device
                val vendorId = device.vendorId
                val productId = device.productId

                Log.d(TAG, "Checking device VID:${vendorId.toString(16).uppercase()} PID:${productId.toString(16).uppercase()}")

                vendorId == TARGET_VID && productId == TARGET_PID && driver is CdcAcmSerialDriver
            }

            if (targetDriver == null) {
                Log.e(TAG, "STM32L412 CDC device not found (VID:04E2 PID:1414)")
                return false
            }

            // Get the fixed port (Port 2)
            val ports = targetDriver.ports
            if (ports.size <= TARGET_PORT_INDEX) {
                Log.e(TAG, "Port $TARGET_PORT_INDEX not available, device has ${ports.size} ports")
                return false
            }

            serialPort = ports[TARGET_PORT_INDEX]
            val port = serialPort ?: return false

            // Close if already open
            if (port.isOpen) {
                port.close()
                delay(CONNECTION_DELAY_MS)
            }

            // Open connection
            port.open(usbManager.openDevice(targetDriver.device))

            // Configure CDC-friendly parameters
            port.setParameters(baud, DATA_BITS, STOP_BITS, PARITY)

            isConnected = true
            Log.i(TAG, "✅ Connected to STM32L412 at $baud baud on Port $TARGET_PORT_INDEX")

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}")
            isConnected = false
            serialPort = null
            return false
        }
    }

    /**
     * Close the serial connection
     */
    fun close() {
        try {
            serialPort?.close()
            isConnected = false
            serialPort = null
            Log.d(TAG, "Serial connection closed")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing connection: ${e.message}")
        }
    }

    /**
     * Write command and read response with timeout
     * @param command Command bytes to send
     * @param expectedResponseSize Expected response length
     * @param timeoutMs Read timeout in milliseconds
     * @return Response bytes or empty array on error
     */
    suspend fun writeRead(command: ByteArray, expectedResponseSize: Int, timeoutMs: Long): ByteArray {
        val port = serialPort ?: throw IOException("Serial port not open")

        try {
            // Clear any pending data
            val clearBuffer = ByteArray(256)
            while (port.read(clearBuffer, 10) > 0) { /* clear buffer */ }

            // Send command
            val bytesWritten = port.write(command, WRITE_TIMEOUT_MS)
            Log.d(TAG, "Sent ${bytesWritten} bytes: ${toHexString(command)}")

            // Read response with timeout
            val response = ByteArray(expectedResponseSize)
            val startTime = System.currentTimeMillis()
            var totalBytesRead = 0

            while (totalBytesRead < expectedResponseSize &&
                (System.currentTimeMillis() - startTime) < timeoutMs) {

                // Create temporary buffer for remaining bytes
                val remainingBytes = expectedResponseSize - totalBytesRead
                val tempBuffer = ByteArray(remainingBytes)

                val bytesRead = port.read(tempBuffer, READ_TIMEOUT_MS)

                if (bytesRead > 0) {
                    // Copy received bytes to response buffer at correct offset
                    System.arraycopy(tempBuffer, 0, response, totalBytesRead, bytesRead)
                    totalBytesRead += bytesRead
                } else {
                    delay(10) // Small delay to prevent busy waiting
                }
            }

            return if (totalBytesRead == expectedResponseSize) {
                Log.d(TAG, "Received ${totalBytesRead} bytes: ${toHexString(response)}")
                response
            } else {
                Log.w(TAG, "Timeout: expected $expectedResponseSize bytes, got $totalBytesRead")
                ByteArray(0)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Communication error: ${e.message}")
            return ByteArray(0)
        }
    }

    /**
     * Check if serial port is connected
     */
    fun isOpen(): Boolean = isConnected && serialPort?.isOpen == true

    /**
     * Convert byte array to hex string for logging
     */
    private fun toHexString(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02X".format(it) }
    }
}