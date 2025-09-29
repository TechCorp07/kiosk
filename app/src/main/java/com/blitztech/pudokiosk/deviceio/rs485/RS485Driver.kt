package com.blitztech.pudokiosk.deviceio.rs485

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
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
    private var connection: UsbDeviceConnection? = null
    private var currentDevice: UsbDevice? = null

    companion object {
        private const val TAG = "RS485Driver"
        private const val MAX_LOG_ENTRIES = 50
        private const val BAUD_RATE = 9600

        // XR21V1414 USB Vendor Requests
        private const val XR_SET_REG = 0x00
        private const val XR_GET_REG = 0x01

        // XR21V1414 Register Blocks
        private const val BLOCK_UART_MANAGER = 0  // UART Manager (FIFO enables)
        private const val BLOCK_CHANNEL_A = 1
        private const val BLOCK_CHANNEL_B = 2     // Port 1 uses Channel B
        private const val BLOCK_CHANNEL_C = 3
        private const val BLOCK_CHANNEL_D = 4

        // Register addresses
        private const val REG_UART_ENABLE = 0x03
        private const val REG_FIFO_ENABLE_CHB = 0x11  // Port 1
    }

    /**
     * Send XR21V1414 vendor-specific register write command
     */
    private suspend fun writeXR21Register(
        connection: UsbDeviceConnection,
        block: Int,
        reg: Int,
        value: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = connection.controlTransfer(
                0x40,  // bmRequestType: Vendor, Out, Device
                XR_SET_REG,  // bRequest
                value,  // wValue: register value
                (reg or (block shl 8)),  // wIndex: reg | (block << 8)
                null,  // no data phase
                0,  // data length
                1000  // timeout
            )

            if (result >= 0) {
                log("✅ XR21 Reg Write: Block=$block, Reg=0x${reg.toString(16)}, Val=0x${value.toString(16)}")
                delay(10) // Small delay between register writes
                return@withContext true
            } else {
                log("❌ XR21 Reg Write failed: rc=$result")
                return@withContext false
            }
        } catch (e: Exception) {
            log("❌ XR21 Reg Write error: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Read XR21V1414 vendor-specific register
     */
    private suspend fun readXR21Register(
        connection: UsbDeviceConnection,
        block: Int,
        reg: Int
    ): Int = withContext(Dispatchers.IO) {
        try {
            val buffer = ByteArray(1)
            val result = connection.controlTransfer(
                0xC0,  // bmRequestType: Vendor, In, Device
                XR_GET_REG,  // bRequest
                0,  // wValue
                (reg or (block shl 8)),  // wIndex: reg | (block << 8)
                buffer,
                1,
                1000
            )

            if (result == 1) {
                val value = buffer[0].toInt() and 0xFF
                log("📖 XR21 Reg Read: Block=$block, Reg=0x${reg.toString(16)}, Val=0x${value.toString(16)}")
                return@withContext value
            } else {
                log("❌ XR21 Reg Read failed: rc=$result")
                return@withContext -1
            }
        } catch (e: Exception) {
            log("❌ XR21 Reg Read error: ${e.message}")
            return@withContext -1
        }
    }

    /**
     * Initialize XR21V1414 Channel B (Port 1) for transmission
     * CRITICAL: This is the missing initialization that prevents "rc=-1" errors
     */
    private suspend fun initializeXR21Channel(
        connection: UsbDeviceConnection,
        portNumber: Int
    ): Boolean = withContext(Dispatchers.IO) {
        log("🔧 Initializing XR21V1414 Channel ${('A' + portNumber)}...")

        val block = BLOCK_CHANNEL_A + portNumber  // Port 0=A, 1=B, 2=C, 3=D
        val fifoEnableReg = 0x10 + portNumber  // 0x10=CHA, 0x11=CHB, etc.

        // Step 1: Enable TX FIFO only
        if (!writeXR21Register(connection, BLOCK_UART_MANAGER, fifoEnableReg, 0x01)) {
            log("❌ Failed to enable TX FIFO")
            return@withContext false
        }

        // Step 2: Enable UART TX and RX
        if (!writeXR21Register(connection, block, REG_UART_ENABLE, 0x03)) {
            log("❌ Failed to enable UART TX/RX")
            return@withContext false
        }

        // Step 3: Enable both TX and RX FIFOs
        if (!writeXR21Register(connection, BLOCK_UART_MANAGER, fifoEnableReg, 0x03)) {
            log("❌ Failed to enable TX/RX FIFOs")
            return@withContext false
        }

        // Verify the configuration
        delay(50)
        val fifoStatus = readXR21Register(connection, BLOCK_UART_MANAGER, fifoEnableReg)
        val uartStatus = readXR21Register(connection, block, REG_UART_ENABLE)

        if (fifoStatus == 0x03 && uartStatus == 0x03) {
            log("✅ XR21V1414 Channel ${('A' + portNumber)} initialized successfully!")
            return@withContext true
        } else {
            log("⚠️ XR21 verify: FIFO=0x${fifoStatus.toString(16)}, UART=0x${uartStatus.toString(16)}")
            // Continue anyway - some devices don't support read back
            return@withContext true
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
     * Connect to a specific serial device
     */
    suspend fun connect(
        baudRate: Int = BAUD_RATE,
        portNumber: Int = 1
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Clean disconnect first
            disconnect()

            val usbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager

            // Find XR21V1414 device
            val deviceList = usbManager.deviceList
            var targetDevice: UsbDevice? = null

            for (device in deviceList.values) {
                if (device.vendorId == 0x04E2 && device.productId == 0x1414) {
                    targetDevice = device
                    break
                }
            }

            if (targetDevice == null) {
                log("❌ XR21V1414 device (VID:04E2, PID:1414) not found")
                return@withContext false
            }

            // Probe the device
            val driver = UsbSerialProber.getDefaultProber().probeDevice(targetDevice)
            if (driver == null) {
                log("❌ No suitable driver found for device")
                return@withContext false
            }

            // Open USB device connection
            val usbConnection = usbManager.openDevice(targetDevice)
            if (usbConnection == null) {
                log("❌ Failed to open device - permission denied")
                return@withContext false
            }

            // *** CRITICAL FIX: Initialize XR21V1414 BEFORE opening serial port ***
            log("🔧 Performing XR21V1414 vendor initialization...")
            if (!initializeXR21Channel(usbConnection, portNumber)) {
                log("⚠️ XR21 initialization had issues, continuing anyway...")
            }

            // Select the specified port
            val availablePorts = driver.ports
            if (portNumber >= availablePorts.size) {
                log("❌ Port $portNumber not available (device has ${availablePorts.size} ports)")
                usbConnection.close()
                return@withContext false
            }

            val selectedPort = availablePorts[portNumber]
            log("📡 Selected: ${selectedPort.javaClass.simpleName} (Port ${portNumber + 1}/4)")

            // Close port if somehow open
            try {
                selectedPort.close()
                delay(200)
            } catch (e: Exception) {
                // Port wasn't open
            }

            // Open the serial port
            try {
                selectedPort.open(usbConnection)
                log("✅ Port opened successfully")
            } catch (e: IOException) {
                log("❌ Failed to open port: ${e.message}")
                usbConnection.close()
                return@withContext false
            }

            // Configure serial parameters
            try {
                selectedPort.setParameters(
                    baudRate,
                    8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )
                log("✅ Set parameters: ${baudRate} 8N1")
            } catch (e: Exception) {
                log("⚠️ Parameter config warning: ${e.message}")
            }

            // Set DTR and RTS
            try {
                selectedPort.dtr = false
                selectedPort.rts = false
                delay(50)

                selectedPort.dtr = true
                selectedPort.rts = true
                log("✅ DTR/RTS enabled")
                delay(100)
            } catch (e: Exception) {
                log("⚠️ DTR/RTS warning: ${e.message}")
            }

            // Purge buffers if supported
            try {
                selectedPort.purgeHwBuffers(true, true)
                log("✅ USB buffers purged")
                delay(50)
            } catch (e: Exception) {
                log("⚠️ Buffer purge not supported: ${e.message}")
            }

            // Clear any stale data
            try {
                val dummyBuffer = ByteArray(256)
                var cleared = 0
                for (i in 0..5) {
                    val read = selectedPort.read(dummyBuffer, 50)
                    if (read > 0) cleared += read
                    if (read <= 0) break
                }
                if (cleared > 0) {
                    log("🧹 Cleared $cleared bytes of stale data")
                }
            } catch (e: Exception) {
                // Normal if no data
            }

            // Store connection
            port = selectedPort
            connection = usbConnection
            currentDevice = targetDevice

            log("✅ Connected successfully to Port ${portNumber + 1}!")
            log("📡 Device: ${targetDevice.deviceName}")
            log("🔧 Driver: ${selectedPort.javaClass.simpleName}")
            log("🎯 Ready for communication")

            // Final settling delay
            delay(200)

            return@withContext true

        } catch (e: Exception) {
            log("❌ Connection error: ${e.message}")
            Log.e(TAG, "Error connecting to device", e)
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

            // Write with reasonable timeout
            p.write(data, 1000)

            // Small delay for USB packet transmission
            delay(20)

            log("✅ Send successful (${data.size} bytes)")
            return@withContext true

        } catch (e: IOException) {
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
            val data = receiveData(100)

            if (data.isNotEmpty()) {
                responseData.addAll(data.toList())

                // Check if we have a complete Winnsen frame (7 bytes)
                if (responseData.size >= 7) {
                    val frame = responseData.toByteArray()
                    for (i in 0..frame.size - 7) {
                        if (frame[i] == 0x90.toByte() &&
                            frame.size >= i + 7 &&
                            frame[i + 6] == 0x03.toByte() &&
                            frame[i + 1] == 0x07.toByte()) {
                            val completeFrame = frame.sliceArray(i until i + 7)
                            log("✅ Received complete response: ${toHexString(completeFrame)}")
                            return@withContext completeFrame
                        }
                    }
                }
            }

            delay(50)
        }

        // Timeout
        if (responseData.isNotEmpty()) {
            val data = responseData.toByteArray()
            log("⏱️ Timeout - partial data: ${toHexString(data)}")
            return@withContext data
        }

        log("⏱️ Timeout - no response received")
        return@withContext ByteArray(0)
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