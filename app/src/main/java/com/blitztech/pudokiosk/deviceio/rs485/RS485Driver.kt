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