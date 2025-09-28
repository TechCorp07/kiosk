package com.blitztech.pudokiosk.deviceio.rs485

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
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
    private var device: UsbDevice? = null
    private var isConnected = false
    private val logMessages = mutableListOf<String>()
    private var currentDevice: UsbDevice? = null

    companion object {
        private const val TAG = "RS485Driver"
        private const val TEST_TIMEOUT_MS = 1000
        private const val MAX_LOG_ENTRIES = 50

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

    data class SerialDevice(
        val device: UsbDevice,
        val driver: UsbSerialDriver,
        val deviceInfo: String,
        val vendorId: String,
        val productId: String,
        val deviceName: String
    )

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
    suspend fun connectToDevice(
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
     * Get formatted log messages
     */
    fun getLogMessages(): List<String> = logMessages.toList()

    /**
     * Clear log messages
     */
    fun clearLog() {
        logMessages.clear()
    }

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