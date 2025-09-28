package com.blitztech.pudokiosk.deviceio.rs485

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * RS485 Communication Tester for STM32L412 Board Discovery and Basic Testing
 * Based on Winnsen protocol but focused on basic communication validation
 */
class RS485CommunicationTester(private val ctx: Context) {
    private var port: UsbSerialPort? = null
    private var currentDevice: UsbDevice? = null
    private val logMessages = mutableListOf<String>()

    companion object {
        private const val TAG = "RS485CommTester"
        private const val DEFAULT_BAUD_RATE = 9600
        private const val TEST_TIMEOUT_MS = 1000
        private const val MAX_LOG_ENTRIES = 50
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
     * Connect to a specific serial device
     */
    suspend fun connectToDevice(
        baudRate: Int = DEFAULT_BAUD_RATE,
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
            Log.e(TAG, "Error connecting to device", e)
            return@withContext false
        }
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
     * Internal logging
     */
    private fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message"

        logMessages.add(logEntry)
        if (logMessages.size > MAX_LOG_ENTRIES) {
            logMessages.removeAt(0)
        }

        Log.d(TAG, message)
    }

    /**
     * Convert byte array to hex string
     */
    private fun toHexString(data: ByteArray): String {
        return data.joinToString(" ") { "%02X".format(it) }
    }
}