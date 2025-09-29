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
 * RS485 Driver with Manual Direction Control
 *
 * CRITICAL FIX: Added RTS-based direction control for RS485 adapter
 * - RTS HIGH = Transmit mode (DE/RE enabled for TX)
 * - RTS LOW = Receive mode (DE/RE disabled for RX)
 */
class RS485Driver(private val ctx: Context) {

    private var port: UsbSerialPort? = null
    private val logMessages = mutableListOf<String>()
    private var currentDevice: UsbDevice? = null

    companion object {
        private const val TAG = "RS485Driver"
        private const val MAX_LOG_ENTRIES = 50
        private const val BAUD_RATE = 9600

        // Critical timing for RS485 direction control
        private const val TX_ENABLE_DELAY_MS = 2L      // Wait after enabling TX
        private const val TX_COMPLETE_DELAY_MS = 5L    // Wait for transmission to complete
        private const val RX_ENABLE_DELAY_MS = 1L      // Wait after enabling RX
    }

    /**
     * Connect to RS485 device with proper initialization
     */
    suspend fun connect(
        baudRate: Int = BAUD_RATE,
        portNumber: Int = 0
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            disconnect()

            val usbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
            val deviceList = usbManager.deviceList
            var targetDevice: UsbDevice? = null

            // Find the RS232-USB converter (VID:04E2 PID:1414)
            for (device in deviceList.values) {
                if (device.vendorId == 0x04E2 && device.productId == 0x1414) {
                    targetDevice = device
                    break
                }
            }

            if (targetDevice == null) {
                log("❌ RS485 device not found (VID:04E2 PID:1414)")
                return@withContext false
            }

            val connection = usbManager.openDevice(targetDevice)
            if (connection == null) {
                log("❌ Failed to open device - check USB permissions")
                return@withContext false
            }

            val driver = UsbSerialProber.getDefaultProber().probeDevice(targetDevice)
            if (driver == null) {
                connection.close()
                log("❌ No compatible driver found")
                return@withContext false
            }

            if (driver.ports.size <= portNumber) {
                connection.close()
                log("❌ Port $portNumber not available")
                return@withContext false
            }

            val selectedPort = driver.ports[portNumber]

            try {
                selectedPort.open(connection)
                selectedPort.setParameters(
                    baudRate,
                    8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )

                // CRITICAL: Initialize RS485 direction control
                // Set RTS LOW = Receive mode (default state)
                selectedPort.dtr = false
                selectedPort.rts = false  // RTS LOW = RX mode

                delay(10) // Allow hardware to stabilize

                port = selectedPort
                currentDevice = targetDevice

                log("✅ Connected to RS485 adapter")
                log("📡 Port: ${portNumber + 1}, Baud: $baudRate")
                log("🎯 Direction control: RTS-based")
                log("🔽 Mode: RX (ready to receive)")

                return@withContext true

            } catch (e: Exception) {
                log("❌ Configuration failed: ${e.message}")
                try {
                    selectedPort.close()
                    connection.close()
                } catch (ex: Exception) {
                    // Ignore cleanup errors
                }
                return@withContext false
            }

        } catch (e: Exception) {
            log("❌ Connection error: ${e.message}")
            Log.e(TAG, "Error connecting", e)
            return@withContext false
        }
    }

    /**
     * Enable RS485 transmit mode
     */
    private suspend fun enableTransmitMode() {
        port?.let { p ->
            p.rts = true  // RTS HIGH = TX mode
            delay(TX_ENABLE_DELAY_MS)  // Wait for MAX3485 to switch
            log("⬆️ TX mode enabled")
        }
    }

    /**
     * Enable RS485 receive mode
     */
    private suspend fun enableReceiveMode() {
        port?.let { p ->
            delay(TX_COMPLETE_DELAY_MS)  // Wait for last byte to transmit
            p.rts = false  // RTS LOW = RX mode
            delay(RX_ENABLE_DELAY_MS)  // Wait for MAX3485 to switch
            log("⬇️ RX mode enabled")
        }
    }

    /**
     * Send data with proper RS485 direction control
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

            // STEP 1: Switch to transmit mode
            enableTransmitMode()

            // STEP 2: Send data
            p.write(data, 1000)

            // STEP 3: Wait for transmission to complete
            // Calculate time needed: (bytes * 10 bits/byte * 1000ms/sec) / baud_rate
            val transmitTimeMs = ((data.size * 10 * 1000) / BAUD_RATE).toLong() + 5
            delay(transmitTimeMs)

            // STEP 4: Switch back to receive mode
            enableReceiveMode()

            log("✅ Send successful (${data.size} bytes)")
            return@withContext true

        } catch (e: IOException) {
            log("❌ I/O error during send: ${e.message}")
            // Try to restore RX mode even on error
            try {
                p.rts = false
            } catch (ex: Exception) {
                // Ignore
            }
            Log.e(TAG, "I/O error sending data", e)
            return@withContext false
        } catch (e: Exception) {
            log("❌ Send error: ${e.message}")
            // Try to restore RX mode even on error
            try {
                p.rts = false
            } catch (ex: Exception) {
                // Ignore
            }
            Log.e(TAG, "Error sending data", e)
            return@withContext false
        }
    }

    /**
     * Receive data (RS485 should already be in RX mode)
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
                return@withContext ByteArray(0)
            }

        } catch (e: IOException) {
            // Timeout is normal, don't log
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
     * Clear receive buffer
     */
    suspend fun clearReceiveBuffer() = withContext(Dispatchers.IO) {
        val p = port ?: return@withContext

        try {
            val buffer = ByteArray(256)
            var totalCleared = 0
            var attempts = 0

            while (attempts < 10) {
                try {
                    val bytesRead = p.read(buffer, 50)
                    if (bytesRead <= 0) break
                    totalCleared += bytesRead
                    attempts++
                } catch (e: Exception) {
                    break
                }
            }

            if (totalCleared > 0) {
                log("🧹 Cleared $totalCleared bytes from buffer")
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * Send command and wait for response
     */
    suspend fun sendCommandAndReceive(
        command: ByteArray,
        responseTimeoutMs: Int = 2000
    ): ByteArray = withContext(Dispatchers.IO) {

        if (command.isEmpty()) {
            log("❌ Cannot send empty command")
            return@withContext ByteArray(0)
        }

        // Clear any pending data
        clearReceiveBuffer()

        // Send command (automatically handles TX/RX switching)
        val sendSuccess = sendData(command)
        if (!sendSuccess) {
            log("❌ Failed to send command")
            return@withContext ByteArray(0)
        }

        // Wait for response
        val startTime = System.currentTimeMillis()
        val responseData = mutableListOf<Byte>()

        while (System.currentTimeMillis() - startTime < responseTimeoutMs) {
            val data = receiveData(100)

            if (data.isNotEmpty()) {
                responseData.addAll(data.toList())

                // Check for complete Winnsen frame (7 bytes)
                if (responseData.size >= 7) {
                    val frame = responseData.toByteArray()
                    for (i in 0..frame.size - 7) {
                        if (frame[i] == 0x90.toByte() &&
                            frame.size >= i + 7 &&
                            frame[i + 6] == 0x03.toByte() &&
                            frame[i + 1] == 0x07.toByte()) {
                            val completeFrame = frame.sliceArray(i until i + 7)
                            log("✅ Complete response: ${toHexString(completeFrame)}")
                            return@withContext completeFrame
                        }
                    }
                }
            }

            delay(10)
        }

        if (responseData.isNotEmpty()) {
            log("⚠️ Partial response: ${responseData.size} bytes")
            return@withContext responseData.toByteArray()
        } else {
            log("❌ No response within ${responseTimeoutMs}ms")
            return@withContext ByteArray(0)
        }
    }

    /**
     * Test communication with STM32
     */
    suspend fun testBasicCommunication(): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected()) {
            log("❌ Not connected")
            return@withContext false
        }

        log("🧪 Testing STM32 communication...")

        // Status check for lock 1, station 0
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
                log("✅ STM32 communication SUCCESS!")
                log("📋 Lock status: ${if (response[4] == 0x01.toByte()) "OPEN" else "CLOSED"}")
                return@withContext true
            } else if (response.isNotEmpty()) {
                log("❌ Invalid response format")
                return@withContext false
            } else {
                log("❌ No response from STM32")
                log("💡 Check: RS485 wiring, STM32 power, baud rate")
                return@withContext false
            }

        } catch (e: Exception) {
            log("❌ Test failed: ${e.message}")
            Log.e(TAG, "Communication test error", e)
            return@withContext false
        }
    }

    /**
     * Listen for incoming data (for debugging)
     */
    suspend fun startListening(durationMs: Long = 10000): List<ByteArray> = withContext(Dispatchers.IO) {
        val p = port ?: run {
            log("❌ No connection")
            return@withContext emptyList()
        }

        val receivedData = mutableListOf<ByteArray>()
        val startTime = System.currentTimeMillis()

        log("👂 Listening for ${durationMs / 1000}s...")

        try {
            // Ensure we're in RX mode
            p.rts = false
            delay(10)

            while (System.currentTimeMillis() - startTime < durationMs) {
                val buffer = ByteArray(256)
                val bytesRead = p.read(buffer, 100)

                if (bytesRead > 0) {
                    val data = buffer.copyOf(bytesRead)
                    receivedData.add(data)
                    log("📥 Received: ${toHexString(data)}")
                }

                delay(10)
            }
        } catch (e: Exception) {
            log("❌ Listening error: ${e.message}")
        }

        log("👂 Listening complete. Received ${receivedData.size} messages")
        receivedData
    }

    fun isConnected(): Boolean = port != null && currentDevice != null

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            port?.let { p ->
                // Set back to RX mode before closing
                p.rts = false
                delay(10)
                p.close()
            }
            log("🔌 Disconnected")
        } catch (e: Exception) {
            log("⚠️ Disconnect error: ${e.message}")
        } finally {
            port = null
            currentDevice = null
        }
    }

    fun getLogMessages(): List<String> = logMessages.toList()

    fun clearLog() = logMessages.clear()

    private fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message"

        logMessages.add(logEntry)
        if (logMessages.size > MAX_LOG_ENTRIES) {
            logMessages.removeAt(0)
        }

        Log.d(TAG, message)
    }

    private fun toHexString(data: ByteArray): String {
        return data.joinToString(" ") { "%02X".format(it) }
    }
}