package com.blitztech.pudokiosk.deviceio.rs232

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import com.blitztech.pudokiosk.usb.UsbHelper
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.*
import kotlinx.coroutines.GlobalScope.coroutineContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.*

/**
 * Enhanced USB Serial barcode scanner for Honeywell Xenon 1900
 * Your scanner appears as USB device (Vendor ID: 1250/0x4E2) with CdcAcmSerialDriver
 * Connected via USB-to-RS232 adapter, but appears to Android as USB serial device
 */
class BarcodeScanner1900(
    private val ctx: Context,
    private val preferredBaud: Int = 9600,
    private val simulateIfNoHardware: Boolean = true
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val usbHelper = UsbHelper(ctx)
    private var usbPort: UsbSerialPort? = null
    private var directPort: DirectRS232Port? = null
    private var readJob: Job? = null
    private var connectionMethod: ConnectionMethod = ConnectionMethod.NONE
    private var isSimulating = false

    // Connection state monitoring
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // Scanned data flow
    private val _scans = MutableSharedFlow<String>(
        replay = 0, extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val scans: SharedFlow<String> = _scans

    // Status messages flow
    private val _statusMessages = MutableSharedFlow<String>(
        replay = 1, extraBufferCapacity = 5, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val statusMessages: SharedFlow<String> = _statusMessages

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    enum class ConnectionMethod {
        NONE,
        USB_SERIAL,
        DIRECT_SERIAL,
        SIMULATION
    }

    // Test barcodes for simulation
    private val testBarcodes = listOf(
        "1234567890123", // EAN-13
        "12345678",      // EAN-8
        "123456789012",  // UPC-A
        "CODE128TEST",   // Code 128
        "HONEYWELL1900", // Test code
        "ANDROID-TEST"   // Android test
    )
    private var testBarcodeIndex = 0

    fun start() {
        Log.i(TAG, "Starting USB/RS232 barcode scanner...")
        _statusMessages.tryEmit("🔍 Starting barcode scanner...")

        usbHelper.register()
        _connectionState.value = ConnectionState.CONNECTING

        scope.launch {
            attemptConnection()
        }
    }

    fun stop() {
        Log.i(TAG, "Stopping barcode scanner...")
        _statusMessages.tryEmit("⏹️ Stopping scanner...")

        readJob?.cancel()
        readJob = null

        try {
            usbPort?.close()
            directPort?.close()
            Log.i(TAG, "Scanner port closed")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing port: ${e.message}")
        }

        usbPort = null
        directPort = null
        connectionMethod = ConnectionMethod.NONE
        isSimulating = false
        _connectionState.value = ConnectionState.DISCONNECTED
        usbHelper.unregister()
    }

    fun triggerScan() {
        when {
            isSimulating -> triggerSimulatedScan()
            _connectionState.value == ConnectionState.CONNECTED -> triggerRealScan()
            else -> {
                Log.w(TAG, "Cannot trigger scan - scanner not connected")
                _statusMessages.tryEmit("❌ Scanner not connected!")
            }
        }
    }

    fun getConnectionInfo(): String {
        return when (_connectionState.value) {
            ConnectionState.CONNECTED -> {
                if (isSimulating) {
                    "🔄 Simulation mode active\nHardware not accessible"
                } else {
                    when (connectionMethod) {
                        ConnectionMethod.USB_SERIAL -> {
                            val portInfo = usbPort?.let {
                                "USB Serial: ${it.driver.device.deviceName}\nVendor: ${it.driver.device.vendorId}"
                            } ?: "Unknown USB port"
                            "✅ Connected via USB Serial\n$portInfo\nBaud: ${preferredBaud}"
                        }
                        ConnectionMethod.DIRECT_SERIAL -> {
                            "✅ Connected via Direct RS232\nPort: ${directPort?.portPath}\nBaud: ${preferredBaud}"
                        }
                        else -> "✅ Connected via ${connectionMethod}"
                    }
                }
            }
            ConnectionState.CONNECTING -> "🔄 Connecting to scanner..."
            ConnectionState.ERROR -> "❌ Scanner connection error"
            ConnectionState.DISCONNECTED -> "⚪ Scanner disconnected"
        }
    }

    private suspend fun attemptConnection() {
        Log.d(TAG, "Attempting scanner connection...")

        // Try USB Serial first (most likely based on your logs), then fallbacks
        val connectionAttempts = listOf(
            { tryUSBSerialConnection() },
            { tryDirectSerialConnection() },
            { if (simulateIfNoHardware) trySimulationMode() else false }
        )

        var connected = false

        for ((index, attempt) in connectionAttempts.withIndex()) {
            try {
                Log.d(TAG, "Trying connection method ${index + 1}...")
                if (attempt()) {
                    connected = true
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Connection method ${index + 1} failed: ${e.message}")
            }
        }

        if (connected) {
            _connectionState.value = ConnectionState.CONNECTED
            startReader()
        } else {
            _connectionState.value = ConnectionState.ERROR
            _statusMessages.tryEmit("❌ All connection methods failed")
        }
    }

    private fun tryUSBSerialConnection(): Boolean {
        Log.d(TAG, "Attempting USB serial connection (Honeywell scanner detection)...")
        _statusMessages.tryEmit("🔍 Looking for Honeywell scanner via USB...")

        val usb = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usb)

        if (drivers.isEmpty()) {
            Log.w(TAG, "No USB serial drivers found")
            return false
        }

        Log.i(TAG, "Found ${drivers.size} USB serial driver(s)")

        // Look specifically for Honeywell scanner (Vendor ID 1250/0x4E2)
        val honeywellDriver = drivers.find { driver ->
            val device = driver.device
            val vendorId = device.vendorId

            Log.d(TAG, "Checking USB device: VID=${vendorId} (0x${vendorId.toString(16).uppercase()})")

            // Honeywell Vendor ID is 1250 (0x4E2)
            vendorId == 1250 || vendorId == 0x4E2
        }

        val driver = honeywellDriver ?: run {
            Log.w(TAG, "No Honeywell scanner found, trying first available USB serial device...")
            drivers.firstOrNull()
        } ?: return false

        val device = driver.device
        Log.i(TAG, "Selected USB device: ${device.deviceName} (VID: ${device.vendorId}, PID: ${device.productId})")

        if (!usbHelper.hasPermission(device)) {
            Log.d(TAG, "Requesting USB permission...")
            _statusMessages.tryEmit("🔐 Requesting USB permission...")

            usbHelper.requestPermission(device) { d, granted ->
                if (granted) {
                    Log.i(TAG, "USB permission granted")
                    _statusMessages.tryEmit("✅ USB permission granted")
                    // Retry connection from IO thread
                    scope.launch { attemptConnection() }
                } else {
                    Log.e(TAG, "USB permission denied for scanner")
                    _statusMessages.tryEmit("❌ USB permission denied!")
                    _connectionState.value = ConnectionState.ERROR
                }
            }
            return false
        }

        val connection = usb.openDevice(device) ?: run {
            Log.e(TAG, "Failed to open USB device")
            _statusMessages.tryEmit("❌ Failed to open USB device")
            return false
        }

        val serialPort = driver.ports.firstOrNull()
        if (serialPort == null) {
            connection.close()
            Log.e(TAG, "No serial ports on USB device")
            _statusMessages.tryEmit("❌ No serial ports available")
            return false
        }

        try {
            serialPort.open(connection)
            serialPort.setParameters(preferredBaud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            serialPort.dtr = true
            serialPort.rts = true

            usbPort = serialPort
            connectionMethod = ConnectionMethod.USB_SERIAL

            Log.i(TAG, "✅ USB serial connection established @ ${preferredBaud} baud")
            _statusMessages.tryEmit("✅ USB scanner connected @ ${preferredBaud} baud")

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure USB serial port: ${e.message}")
            _statusMessages.tryEmit("❌ USB port configuration failed: ${e.message}")
            try {
                connection.close()
            } catch (closeException: Exception) {
                Log.w(TAG, "Error closing connection after failure: ${closeException.message}")
            }
            return false
        }
    }

    private fun tryDirectSerialConnection(): Boolean {
        Log.d(TAG, "Attempting direct RS232 serial connection as fallback...")
        _statusMessages.tryEmit("🔍 Trying direct RS232 as fallback...")

        val possiblePorts = listOf(
            "/dev/ttyS4",    // Your diagnostic shows this is accessible
            "/dev/ttymxc3",  // Your diagnostic shows this is accessible
            "/dev/ttyS0", "/dev/ttyS1", "/dev/ttyS2", "/dev/ttyS3",
            "/dev/ttyHS0", "/dev/ttyHS1",
            "/dev/ttyMSM0", "/dev/ttyMSM1",
            "/dev/ttymxc0", "/dev/ttymxc1", "/dev/ttymxc2",
            "/dev/ttyAMA0", "/dev/ttyAMA1"
        )

        for (portPath in possiblePorts) {
            try {
                val portFile = File(portPath)
                if (!portFile.exists()) continue

                Log.d(TAG, "Trying direct RS232 port: $portPath")

                // Try to access the serial port directly
                val input = FileInputStream(portFile)
                val output = FileOutputStream(portFile)

                // Test basic accessibility
                input.available() // This will throw if not accessible

                directPort = DirectRS232Port(input, output, portPath)
                connectionMethod = ConnectionMethod.DIRECT_SERIAL

                Log.i(TAG, "✅ Direct RS232 connection established: $portPath")
                _statusMessages.tryEmit("✅ Connected to $portPath")
                return true

            } catch (e: Exception) {
                Log.d(TAG, "Port $portPath not accessible: ${e.message}")
            }
        }

        return false
    }

    private fun trySimulationMode(): Boolean {
        if (!simulateIfNoHardware) return false

        Log.i(TAG, "🔄 Starting simulation mode for testing...")
        _statusMessages.tryEmit("🔄 Hardware not accessible - using simulation")

        isSimulating = true
        connectionMethod = ConnectionMethod.SIMULATION
        return true
    }

    private fun triggerRealScan() {
        scope.launch {
            _statusMessages.tryEmit("📸 Triggering scan (SYN T CR)...")
            activateSerialTrigger()
        }
    }

    private fun triggerSimulatedScan() {
        scope.launch {
            Log.d(TAG, "Triggering simulated scan...")
            _statusMessages.tryEmit("📸 Simulating scan...")

            // Simulate scan delay
            delay(500)

            // Generate test barcode
            val testBarcode = testBarcodes[testBarcodeIndex]
            testBarcodeIndex = (testBarcodeIndex + 1) % testBarcodes.size

            Log.i(TAG, "📸 SIMULATED SCAN: '$testBarcode'")
            _statusMessages.tryEmit("📸 Simulated: $testBarcode")
            _scans.emit(testBarcode)
        }
    }

    private fun writeBytesRaw(bytes: ByteArray) {
        when (connectionMethod) {
            ConnectionMethod.USB_SERIAL -> usbPort?.write(bytes, 1000)
            ConnectionMethod.DIRECT_SERIAL -> directPort?.write(bytes)
            else -> Unit
        }
    }

    fun activateSerialTrigger() {
        // SYN 'T' CR
        val cmd = byteArrayOf(0x16, 'T'.code.toByte(), 0x0D)
        writeBytesRaw(cmd)
        _statusMessages.tryEmit("📸 Trigger: START")
    }

    fun deactivateSerialTrigger() {
        // SYN 'U' CR
        val cmd = byteArrayOf(0x16, 'U'.code.toByte(), 0x0D)
        writeBytesRaw(cmd)
        _statusMessages.tryEmit("🛑 Trigger: STOP")
    }

    private fun startReader() {
        Log.d(TAG, "Starting scan data reader for ${connectionMethod}...")
        _statusMessages.tryEmit("👂 Listening for scans...")

        readJob?.cancel()
        readJob = scope.launch {
            when {
                isSimulating -> {
                    // Simulation mode - just keep the coroutine alive
                    while (isActive) {
                        delay(1000)
                    }
                }
                else -> startRealReader()
            }
        }
    }

    fun configureSoftwareTrigger() {
        val port = usbPort ?: return

        scope.launch {
            try {
                val configCommands = listOf(
                    // Honeywell configuration commands
                    byteArrayOf(0x1B, 0x5B, 0x31, 0x34, 0x68, 0x0D), // Enable software trigger
                    byteArrayOf(0x1B, 0x5B, 0x31, 0x33, 0x6C, 0x0D), // Disable manual trigger
                    byteArrayOf(0x1B, 0x5B, 0x31, 0x32, 0x68, 0x0D), // Set trigger mode
                    "SCNENA\r\n".toByteArray(),                      // Scanner enable command
                )

                configCommands.forEach { cmd ->
                    port.write(cmd, 1000)
                    delay(200) // Wait between commands
                }

                Log.i(TAG, "Software trigger configuration sent")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to configure software trigger: ${e.message}")
            }
        }
    }

    private suspend fun startRealReader() {
        val buffer = ByteArray(256)
        val line = StringBuilder()
        var consecutiveErrors = 0

        while (coroutineContext.isActive && _connectionState.value == ConnectionState.CONNECTED) {
            try {
                val bytesRead = when (connectionMethod) {
                    ConnectionMethod.USB_SERIAL -> {
                        val port = usbPort ?: break
                        port.read(buffer, 500) // 500ms timeout
                    }
                    ConnectionMethod.DIRECT_SERIAL -> {
                        val port = directPort ?: break
                        port.read(buffer, 500)
                    }
                    else -> 0
                }

                if (bytesRead > 0) {
                    consecutiveErrors = 0 // Reset error counter
                    processReceivedData(buffer, bytesRead, line)
                } else {
                    // No data, small delay to prevent busy waiting
                    delay(50)
                }

            } catch (e: Exception) {
                consecutiveErrors++
                Log.w(TAG, "Scanner read error (${consecutiveErrors}): ${e.message}")

                if (consecutiveErrors >= 10) {
                    Log.e(TAG, "Too many consecutive read errors, stopping reader")
                    _statusMessages.tryEmit("❌ Connection lost - too many errors")
                    _connectionState.value = ConnectionState.ERROR
                    break
                }

                delay(1000) // Wait before retrying
            }
        }

        Log.d(TAG, "Scan reader stopped")
    }

    private suspend fun processReceivedData(buffer: ByteArray, length: Int, line: StringBuilder) {
        for (i in 0 until length) {
            val b = buffer[i].toInt() and 0xFF

            when (b) {
                0x0D, 0x0A -> { // CR or LF
                    if (line.isNotEmpty()) {
                        val scannedText = line.toString().trim()
                        line.clear()

                        if (scannedText.isNotEmpty()) {
                            Log.i(TAG, "📸 SCAN RECEIVED: '$scannedText'")
                            _statusMessages.tryEmit("📸 Scanned: $scannedText")
                            _scans.emit(scannedText)
                        }
                    }
                }
                in 32..126 -> { // Printable ASCII characters only
                    line.append(b.toChar())
                }
                // Ignore other characters
            }
        }
    }

    /**
     * Wrapper class for direct RS232 access
     */
    private class DirectRS232Port(
        private val inputStream: InputStream,
        private val outputStream: OutputStream,
        val portPath: String
    ) {
        fun write(data: ByteArray, timeoutMs: Int = 1000): Int {
            outputStream.write(data)
            outputStream.flush()
            return data.size
        }

        fun write(data: ByteArray) {
            outputStream.write(data)
            outputStream.flush()
        }

        fun read(buffer: ByteArray, timeoutMs: Int): Int {
            return if (inputStream.available() > 0) {
                inputStream.read(buffer)
            } else {
                0
            }
        }

        fun close() {
            try {
                inputStream.close()
                outputStream.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing direct RS232 port: ${e.message}")
            }
        }

        override fun toString(): String = "DirectRS232Port($portPath)"
    }

    companion object {
        private const val TAG = "BarcodeScanner1900"
    }
}