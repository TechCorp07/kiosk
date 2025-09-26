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
     * Scan for available USB serial devices that could be RS485 adapters
     */
    suspend fun scanForSerialDevices(): List<SerialDevice> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<SerialDevice>()

        try {
            val usbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

            log("🔍 Scanning for USB serial devices...")
            log("Found ${availableDrivers.size} potential serial device(s)")

            availableDrivers.forEach { driver ->
                val device = driver.device
                val vendorId = String.format("%04X", device.vendorId)
                val productId = String.format("%04X", device.productId)

                // Build device info string
                val deviceInfo = buildString {
                    append("${device.deviceName} - ")
                    append("VID:$vendorId PID:$productId")
                    device.manufacturerName?.let { append(" ($it)") }
                    device.productName?.let { append(" - $it") }
                }

                devices.add(SerialDevice(
                    device = device,
                    driver = driver,
                    deviceInfo = deviceInfo,
                    vendorId = vendorId,
                    productId = productId,
                    deviceName = device.deviceName
                ))

                log("📱 Device: $deviceInfo")
            }

            if (devices.isEmpty()) {
                log("⚠️ No USB serial devices found!")
                log("Check connections and ensure RS485-USB adapter is connected")
            }

        } catch (e: Exception) {
            log("❌ Error scanning devices: ${e.message}")
            Log.e(TAG, "Error scanning for devices", e)
        }

        devices
    }

    /**
     * Connect to a specific serial device
     */
    suspend fun connectToDevice(serialDevice: SerialDevice, baudRate: Int = DEFAULT_BAUD_RATE): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Close existing connection if any
                disconnect()

                log("🔌 Connecting to: ${serialDevice.deviceInfo}")
                log("⚙️ Settings: $baudRate baud, 8N1")

                val usbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
                val connection = usbManager.openDevice(serialDevice.device)

                if (connection == null) {
                    log("❌ Failed to open device - permission denied")
                    return@withContext false
                }

                port = serialDevice.driver.ports.firstOrNull()?.apply {
                    open(connection)
                    setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                    dtr = true
                    rts = true
                    purgeHwBuffers(true, true)
                }

                currentDevice = serialDevice.device

                if (port != null) {
                    log("✅ Connected successfully!")
                    delay(100) // Allow port to stabilize
                    return@withContext true
                } else {
                    log("❌ Failed to open serial port")
                    return@withContext false
                }

            } catch (e: Exception) {
                log("❌ Connection error: ${e.message}")
                Log.e(TAG, "Error connecting to device", e)
                return@withContext false
            }
        }

    /**
     * Send basic test commands following Winnsen protocol format
     */
    suspend fun sendBasicTest(station: Int = 1, lockNumber: Int = 1): Boolean =
        withContext(Dispatchers.IO) {
            val p = port ?: run {
                log("❌ No connection - please connect to a device first")
                return@withContext false
            }

            try {
                log("📤 Testing basic communication...")

                // Test 1: Send unlock command (Winnsen format)
                val unlockCommand = byteArrayOf(
                    0x90.toByte(), 0x06, 0x05,
                    station.toByte(), lockNumber.toByte(), 0x03
                )

                log("📤 Sending unlock command: ${toHexString(unlockCommand)}")
                log("📤 Station: $station, Lock: $lockNumber")

                val written = p.write(unlockCommand, TEST_TIMEOUT_MS)
                log("📤 Wrote $written bytes")

                delay(100) // Wait for response

                // Try to read response
                val buffer = ByteArray(32)
                val bytesRead = p.read(buffer, TEST_TIMEOUT_MS)

                if (bytesRead > 0) {
                    val response = buffer.copyOf(bytesRead)
                    log("📥 Received ${bytesRead} bytes: ${toHexString(response)}")
                    parseResponse(response)
                    return@withContext true
                } else {
                    log("⏱️ No response received (timeout)")

                    // Test 2: Send status command
                    val statusCommand = byteArrayOf(
                        0x90.toByte(), 0x06, 0x12,
                        station.toByte(), lockNumber.toByte(), 0x03
                    )

                    log("📤 Trying status command: ${toHexString(statusCommand)}")
                    p.write(statusCommand, TEST_TIMEOUT_MS)
                    delay(100)

                    val statusRead = p.read(buffer, TEST_TIMEOUT_MS)
                    if (statusRead > 0) {
                        val statusResponse = buffer.copyOf(statusRead)
                        log("📥 Status response: ${toHexString(statusResponse)}")
                        parseResponse(statusResponse)
                        return@withContext true
                    } else {
                        log("⏱️ No response to status command either")
                    }
                }

                return@withContext false

            } catch (e: Exception) {
                log("❌ Communication error: ${e.message}")
                Log.e(TAG, "Error during basic test", e)
                return@withContext false
            }
        }

    /**
     * Send raw hex data for custom testing
     */
    suspend fun sendRawHex(hexString: String): Boolean = withContext(Dispatchers.IO) {
        val p = port ?: run {
            log("❌ No connection - please connect to a device first")
            return@withContext false
        }

        try {
            val cleanHex = hexString.replace(" ", "").replace("0x", "")
            if (cleanHex.length % 2 != 0) {
                log("❌ Invalid hex string - must have even number of characters")
                return@withContext false
            }

            val bytes = cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            log("📤 Sending raw data: ${toHexString(bytes)}")

            val written = p.write(bytes, TEST_TIMEOUT_MS)
            log("📤 Wrote $written bytes")

            delay(100)

            val buffer = ByteArray(64)
            val bytesRead = p.read(buffer, TEST_TIMEOUT_MS)

            if (bytesRead > 0) {
                val response = buffer.copyOf(bytesRead)
                log("📥 Raw response: ${toHexString(response)}")
                return@withContext true
            } else {
                log("⏱️ No response to raw command")
                return@withContext false
            }

        } catch (e: Exception) {
            log("❌ Error sending raw hex: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Parse received response and provide interpretation
     */
    private fun parseResponse(response: ByteArray) {
        if (response.isEmpty()) return

        try {
            when {
                response.size >= 6 && response[0] == 0x90.toByte() -> {
                    log("🔍 Winnsen protocol frame detected:")
                    log("   Header: 0x${String.format("%02X", response[0])}")
                    log("   Length: ${response[1].toInt() and 0xFF}")

                    if (response.size >= 3) {
                        val funcCode = response[2].toInt() and 0xFF
                        when (funcCode) {
                            0x85 -> {
                                log("   Function: 0x85 (Unlock Response)")
                                if (response.size >= 7) {
                                    val station = response[3].toInt() and 0xFF
                                    val lock = response[4].toInt() and 0xFF
                                    val status = response[5].toInt() and 0xFF
                                    log("   Station: $station, Lock: $lock")
                                    log("   Status: ${if (status == 1) "SUCCESS (Open)" else "FAILED (Closed)"}")
                                }
                            }
                            0x92 -> {
                                log("   Function: 0x92 (Status Response)")
                                if (response.size >= 7) {
                                    val station = response[3].toInt() and 0xFF
                                    val status = response[4].toInt() and 0xFF
                                    val lock = response[5].toInt() and 0xFF
                                    log("   Station: $station, Lock: $lock")
                                    log("   Door Status: ${if (status == 1) "OPEN" else "CLOSED"}")
                                }
                            }
                            else -> log("   Function: 0x${String.format("%02X", funcCode)} (Unknown)")
                        }
                    }
                }
                else -> {
                    log("🔍 Raw data (not Winnsen protocol):")
                    log("   ASCII: ${response.toString(Charsets.UTF_8).filter { it.isLetterOrDigit() || it.isWhitespace() }}")
                }
            }
        } catch (e: Exception) {
            log("⚠️ Error parsing response: ${e.message}")
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
     * Get current device info
     */
    fun getCurrentDeviceInfo(): String? {
        return currentDevice?.let { device ->
            "${device.deviceName} - VID:${String.format("%04X", device.vendorId)} PID:${String.format("%04X", device.productId)}"
        }
    }

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