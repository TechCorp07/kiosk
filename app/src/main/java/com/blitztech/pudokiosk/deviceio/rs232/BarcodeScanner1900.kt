package com.blitztech.pudokiosk.deviceio.rs232

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import com.blitztech.pudokiosk.usb.UsbHelper
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Clean Honeywell Xenon 1900 USB-Serial Barcode Scanner Driver
 * Auto-connects and maintains connection, stores latest scan globally accessible
 */

object BarcodeScanner {
    private const val TAG = "BarcodeScanner"
    private const val VENDOR_ID = 1250  // 0x4E2
    private const val PRODUCT_ID = 5140 // 0x1414
    private const val BAUD_RATE = 115200
    private const val MAX_PERMISSION_RETRIES = 3

    // Global accessible latest scanned barcode
    var latestScanData: String = ""
        private set

    // Connection state monitoring
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // Real-time scanned data flow
    private val _scannedData = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val scannedData: SharedFlow<String> = _scannedData

    // Status messages for debugging
    private val _statusMessages = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 20,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val statusMessages: SharedFlow<String> = _statusMessages

    // Internal state
    private var ctx: Context? = null
    private var usbHelper: UsbHelper? = null
    private var usbPort: UsbSerialPort? = null
    private var readerJob: Job? = null
    private var permissionRetries = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        PERMISSION_DENIED,
        ERROR
    }

    /**
     * Initialize and start the barcode scanner
     * Call this from Application onCreate or main activity
     */
    fun initialize(context: Context) {
        Log.i(TAG, "Initializing Honeywell Xenon 1900 barcode scanner...")
        ctx = context.applicationContext
        usbHelper = UsbHelper(ctx!!)
        usbHelper?.register()

        _statusMessages.tryEmit("🔍 Initializing barcode scanner...")

        // Start connection attempt
        scope.launch {
            connectToScanner()
        }
    }

    /**
     * Stop and cleanup the scanner
     */
    fun shutdown() {
        Log.i(TAG, "Shutting down barcode scanner...")
        _statusMessages.tryEmit("⏹️ Shutting down scanner...")

        readerJob?.cancel()
        readerJob = null

        try {
            usbPort?.close()
            Log.i(TAG, "Scanner port closed")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing scanner port: ${e.message}")
        }

        usbPort = null
        usbHelper?.unregister()
        usbHelper = null
        ctx = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /**
     * Get current connection information for debugging
     */
    fun getConnectionInfo(): String {
        return when (_connectionState.value) {
            ConnectionState.CONNECTED -> {
                val portInfo = usbPort?.let {
                    "Device: ${it.driver.device.deviceName}\n" +
                            "VID: ${it.driver.device.vendorId} (0x${it.driver.device.vendorId.toString(16).uppercase()})\n" +
                            "PID: ${it.driver.device.productId} (0x${it.driver.device.productId.toString(16).uppercase()})"
                } ?: "Unknown device"
                "✅ Connected to Honeywell Xenon 1900\n$portInfo\nBaud: $BAUD_RATE"
            }
            ConnectionState.CONNECTING -> "🔄 Connecting to scanner..."
            ConnectionState.PERMISSION_DENIED -> "❌ USB permission denied after $MAX_PERMISSION_RETRIES attempts"
            ConnectionState.ERROR -> "❌ Scanner connection error"
            ConnectionState.DISCONNECTED -> "⚪ Scanner disconnected"
        }
    }

    /**
     * Force reconnection attempt
     */
    fun reconnect() {
        Log.i(TAG, "Manual reconnection requested...")
        _statusMessages.tryEmit("🔄 Manual reconnection...")
        permissionRetries = 0

        scope.launch {
            connectToScanner()
        }
    }

    private suspend fun connectToScanner() {
        _connectionState.value = ConnectionState.CONNECTING

        val context = ctx ?: run {
            Log.e(TAG, "Context not available for scanner connection")
            _connectionState.value = ConnectionState.ERROR
            return
        }

        val helper = usbHelper ?: run {
            Log.e(TAG, "UsbHelper not available for scanner connection")
            _connectionState.value = ConnectionState.ERROR
            return
        }

        try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

            if (drivers.isEmpty()) {
                Log.w(TAG, "No USB serial drivers found")
                _statusMessages.tryEmit("❌ No USB devices found")
                scheduleReconnect()
                return
            }

            // Find Honeywell Xenon 1900 specifically
            val honeywellDriver = drivers.find { driver ->
                val device = driver.device
                device.vendorId == VENDOR_ID && device.productId == PRODUCT_ID
            }

            if (honeywellDriver == null) {
                Log.w(TAG, "Honeywell Xenon 1900 not found (VID: $VENDOR_ID, PID: $PRODUCT_ID)")
                _statusMessages.tryEmit("❌ Honeywell scanner not found")
                scheduleReconnect()
                return
            }

            val device = honeywellDriver.device
            Log.i(TAG, "Found Honeywell Xenon 1900: ${device.deviceName}")
            _statusMessages.tryEmit("✅ Found Honeywell scanner")

            // Check/request USB permission
            if (!helper.hasPermission(device)) {
                if (permissionRetries >= MAX_PERMISSION_RETRIES) {
                    Log.e(TAG, "USB permission denied after $MAX_PERMISSION_RETRIES attempts")
                    _statusMessages.tryEmit("❌ USB permission denied - max retries reached")
                    _connectionState.value = ConnectionState.PERMISSION_DENIED
                    return
                }

                Log.d(TAG, "Requesting USB permission (attempt ${permissionRetries + 1}/$MAX_PERMISSION_RETRIES)")
                _statusMessages.tryEmit("🔐 Requesting USB permission...")

                helper.requestPermission(device) { d, granted ->
                    permissionRetries++
                    if (granted) {
                        Log.i(TAG, "USB permission granted")
                        _statusMessages.tryEmit("✅ USB permission granted")
                        permissionRetries = 0 // Reset on success
                        scope.launch { connectToScanner() }
                    } else {
                        Log.w(TAG, "USB permission denied (attempt $permissionRetries/$MAX_PERMISSION_RETRIES)")
                        _statusMessages.tryEmit("❌ USB permission denied")
                        scope.launch { connectToScanner() } // Retry
                    }
                }
                return
            }

            // Open USB connection
            val connection = usbManager.openDevice(device) ?: run {
                Log.e(TAG, "Failed to open USB device")
                _statusMessages.tryEmit("❌ Failed to open USB device")
                scheduleReconnect()
                return
            }

            val serialPort = honeywellDriver.ports.firstOrNull() ?: run {
                connection.close()
                Log.e(TAG, "No serial ports available on device")
                _statusMessages.tryEmit("❌ No serial ports available")
                scheduleReconnect()
                return
            }

            // Configure serial port
            try {
                serialPort.open(connection)
                serialPort.setParameters(
                    BAUD_RATE,
                    8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )
                serialPort.dtr = true
                serialPort.rts = true

                usbPort = serialPort
                _connectionState.value = ConnectionState.CONNECTED

                Log.i(TAG, "✅ Scanner connected successfully @ $BAUD_RATE baud")
                _statusMessages.tryEmit("✅ Scanner connected @ $BAUD_RATE baud")

                // Start reading data
                startDataReader()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to configure serial port: ${e.message}")
                _statusMessages.tryEmit("❌ Port configuration failed: ${e.message}")
                try {
                    connection.close()
                } catch (closeException: Exception) {
                    Log.w(TAG, "Error closing connection: ${closeException.message}")
                }
                scheduleReconnect()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Connection attempt failed: ${e.message}")
            _statusMessages.tryEmit("❌ Connection failed: ${e.message}")
            scheduleReconnect()
        }
    }

    private fun startDataReader() {
        Log.d(TAG, "Starting barcode data reader...")
        _statusMessages.tryEmit("👂 Listening for scans...")

        readerJob?.cancel()
        readerJob = scope.launch {
            val buffer = ByteArray(256)
            val lineBuilder = StringBuilder()
            var consecutiveErrors = 0

            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                try {
                    val port = usbPort ?: break
                    val bytesRead = port.read(buffer, 500) // 500ms timeout

                    if (bytesRead > 0) {
                        consecutiveErrors = 0
                        processReceivedBytes(buffer, bytesRead, lineBuilder)
                    } else {
                        // No data, small delay to prevent busy waiting
                        delay(50)
                    }

                } catch (e: Exception) {
                    consecutiveErrors++
                    Log.w(TAG, "Read error (${consecutiveErrors}): ${e.message}")

                    if (consecutiveErrors >= 5) {
                        Log.e(TAG, "Too many consecutive read errors, reconnecting...")
                        _statusMessages.tryEmit("❌ Connection lost - reconnecting...")
                        break
                    }

                    delay(1000)
                }
            }

            // Connection lost, attempt reconnection
            if (_connectionState.value == ConnectionState.CONNECTED) {
                Log.w(TAG, "Data reader stopped unexpectedly, reconnecting...")
                _connectionState.value = ConnectionState.DISCONNECTED
                scheduleReconnect()
            }
        }
    }

    private suspend fun processReceivedBytes(buffer: ByteArray, length: Int, lineBuilder: StringBuilder) {
        for (i in 0 until length) {
            val byte = buffer[i].toInt() and 0xFF

            when (byte) {
                0x0D, 0x0A -> { // CR or LF - end of barcode
                    if (lineBuilder.isNotEmpty()) {
                        val scannedBarcode = lineBuilder.toString().trim()
                        lineBuilder.clear()

                        if (scannedBarcode.isNotEmpty()) {
                            // Store globally accessible
                            latestScanData = scannedBarcode

                            Log.i(TAG, "📸 BARCODE SCANNED: '$scannedBarcode'")
                            _statusMessages.tryEmit("📸 Scanned: $scannedBarcode")

                            // Emit to flow for real-time consumers
                            _scannedData.emit(scannedBarcode)
                        }
                    }
                }
                in 32..126 -> { // Printable ASCII characters
                    lineBuilder.append(byte.toChar())
                }
                // Ignore control characters and non-printable
            }
        }
    }

    private fun scheduleReconnect() {
        _connectionState.value = ConnectionState.DISCONNECTED

        scope.launch {
            Log.d(TAG, "Scheduling reconnection in 5 seconds...")
            _statusMessages.tryEmit("🔄 Reconnecting in 5 seconds...")
            delay(5000)

            if (_connectionState.value == ConnectionState.DISCONNECTED) {
                connectToScanner()
            }
        }
    }
 }
