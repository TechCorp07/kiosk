package com.blitztech.pudokiosk.deviceio.printer

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Custom Android API imports (based on your documentation)
import it.custom.printer.api.android.CustomAndroidAPI
import it.custom.printer.api.android.CustomException
import it.custom.printer.api.android.PrinterFont
import it.custom.printer.api.android.PrinterStatus

// For RS232 fallback
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

/**
 * Enhanced TG2480HIII printer driver with:
 * 1. Proper Custom API USB enumeration (like working demo app)
 * 2. RS232 fallback support
 * 3. Comprehensive debugging and logging
 * 4. Connection protocol detection
 */
class CustomTG2480HIIIDriver(
    private val context: Context,
    private val simulate: Boolean = false,
    private val enableAutoReconnect: Boolean = true,
    private val enableRS232Fallback: Boolean = true
) {

    companion object {
        private const val TAG = "Enhanced-TG2480HIII"
        private const val CUSTOM_VENDOR_ID = 3540 // Custom SpA vendor ID
        private const val STATUS_CHECK_INTERVAL = 15000L // 15 seconds
        private const val RECONNECT_DELAY = 3000L // 3 seconds
        private const val USB_PERMISSION_ACTION = "com.blitztech.pudokiosk.printer.USB_PERMISSION"
        private const val MAX_RECONNECT_ATTEMPTS = 5

        // RS232 settings for TG2480HIII (from datasheet)
        private const val RS232_BAUD_RATE = 115200
        private const val RS232_DATA_BITS = 8
        private const val RS232_STOP_BITS = 1
        private const val RS232_PARITY = UsbSerialPort.PARITY_NONE
    }

    // Connection protocol tracking
    enum class ConnectionProtocol {
        NONE,
        CUSTOM_USB_API,    // Using Custom Android API via USB
        RS232_SERIAL,      // Using RS232 via USB-to-Serial
        SIMULATION
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR,
        PERMISSION_DENIED,
        DEVICE_NOT_FOUND,
        RECONNECTING
    }

    // Custom API objects for USB connection
    private var customAPI: CustomAndroidAPI? = null
    private var customPrnDevice: Any? = null // Custom API printer device

    // RS232 objects for serial connection
    private var serialPort: UsbSerialPort? = null

    // Current active connection protocol
    private var activeProtocol = ConnectionProtocol.NONE

    // USB Manager for device enumeration
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    // State management
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _printerStatus = MutableStateFlow<CustomPrinterStatus?>(null)
    val printerStatus: StateFlow<CustomPrinterStatus?> = _printerStatus.asStateFlow()

    private val _activeProtocolFlow = MutableStateFlow(ConnectionProtocol.NONE)
    val currentProtocol: StateFlow<ConnectionProtocol> = _activeProtocolFlow.asStateFlow()

    // Background monitoring
    private var statusHandler: Handler? = null
    private var statusRunnable: Runnable? = null

    // Coroutines and synchronization
    private val driverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var reconnectAttempts = 0
    private val printerMutex = Mutex()

    // USB permission handling
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (USB_PERMISSION_ACTION == intent.action) {
                synchronized(this@CustomTG2480HIIIDriver) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                    Log.d(TAG, "USB permission received: granted=$granted, device=${device?.deviceName}")

                    if (granted && device != null && device.vendorId == CUSTOM_VENDOR_ID) {
                        driverScope.launch {
                            connectViaCustomAPI(device)
                        }
                    } else {
                        _connectionState.value = ConnectionState.PERMISSION_DENIED
                        Log.e(TAG, "USB permission denied for Custom TG2480HIII")

                        // Try RS232 fallback if enabled
                        if (enableRS232Fallback) {
                            driverScope.launch {
                                tryRS232Connection()
                            }
                        }
                    }
                }
            }
        }
    }

    data class CustomPrinterStatus(
        val noPaper: Boolean,
        val paperRolling: Boolean,
        val lfPressed: Boolean,
        val printerName: String?,
        val printerInfo: String?,
        val firmwareVersion: String?,
        val isReady: Boolean,
        val protocol: ConnectionProtocol = ConnectionProtocol.NONE,
        val lastUpdated: Long = System.currentTimeMillis()
    ) {
        val isOperational: Boolean
            get() = isReady && !noPaper
    }

    suspend fun initialize(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "=== Initializing Enhanced Custom TG2480HIII Printer Driver ===")
            Log.i(TAG, "Configuration: simulate=$simulate, autoReconnect=$enableAutoReconnect, rs232Fallback=$enableRS232Fallback")

            if (simulate) {
                Log.i(TAG, "SIMULATION MODE: Creating simulated printer connection")
                _connectionState.value = ConnectionState.CONNECTED
                _activeProtocolFlow.value = ConnectionProtocol.SIMULATION
                activeProtocol = ConnectionProtocol.SIMULATION
                _printerStatus.value = createSimulatedStatus()
                return@withContext Result.success(true)
            }

            // Initialize Custom Android API
            Log.d(TAG, "Initializing Custom Android API...")
            customAPI = CustomAndroidAPI()
            Log.i(TAG, "Custom API Version: ${CustomAndroidAPI.getAPIVersion()}")

            // Register USB receiver
            registerUsbReceiver()

            // Acquire wake lock for stable communication
            acquireWakeLock()

            // Step 1: Try Custom API USB enumeration first (like working demo app)
            Log.i(TAG, "Step 1: Attempting Custom API USB device enumeration...")
            val customApiResult = tryCustomAPIConnection()

            if (customApiResult) {
                Log.i(TAG, "✓ Custom API USB connection successful!")
                startStatusMonitoring()
                return@withContext Result.success(true)
            }

            // Step 2: Try RS232 fallback if enabled and USB failed
            if (enableRS232Fallback) {
                Log.i(TAG, "Step 2: Attempting RS232 serial connection fallback...")
                val rs232Result = tryRS232Connection()

                if (rs232Result) {
                    Log.i(TAG, "✓ RS232 serial connection successful!")
                    startStatusMonitoring()
                    return@withContext Result.success(true)
                }
            }

            Log.e(TAG, "✗ All connection methods failed")
            _connectionState.value = ConnectionState.DEVICE_NOT_FOUND
            return@withContext Result.failure(Exception("Custom TG2480HIII printer not detected via USB or RS232"))

        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed with exception", e)
            _connectionState.value = ConnectionState.ERROR
            return@withContext Result.failure(e)
        }
    }

    /**
     * Try connecting using Custom Android API (like the working demo app)
     * This is the preferred method and should work if the device is properly connected
     */
    private suspend fun tryCustomAPIConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "--- Custom API Connection Attempt ---")
            _connectionState.value = ConnectionState.CONNECTING

            // Use Custom API enumeration method (exactly like demo app)
            Log.d(TAG, "Calling CustomAndroidAPI.EnumUsbDevices()...")
            val usbDeviceArray = customAPI?.let { api ->
                try {
                    // This is the method the working demo app uses!
                    api.javaClass.getMethod("EnumUsbDevices", Context::class.java)
                        .invoke(api, context) as? Array<*>
                } catch (e: Exception) {
                    Log.w(TAG, "Custom API EnumUsbDevices method not available, falling back to direct enumeration", e)
                    null
                }
            }

            if (usbDeviceArray != null && usbDeviceArray.isNotEmpty()) {
                Log.i(TAG, "Custom API found ${usbDeviceArray.size} USB devices:")
                usbDeviceArray.forEachIndexed { index, device ->
                    // Extract device info using reflection (since we don't have the exact Custom API classes)
                    val deviceInfo = try {
                        val getName = device?.javaClass?.getMethod("getName")
                        val getVendorId = device?.javaClass?.getMethod("getVendorId")
                        val name = getName?.invoke(device) ?: "Unknown"
                        val vendorId = getVendorId?.invoke(device) ?: "Unknown"
                        "Device $index: $name (Vendor: $vendorId)"
                    } catch (e: Exception) {
                        "Device $index: ${device?.toString() ?: "Unknown"}"
                    }
                    Log.d(TAG, "  $deviceInfo")
                }

                // Look for our Custom printer (vendor ID 3540)
                val customDevice = usbDeviceArray.find { device ->
                    try {
                        val getVendorId = device?.javaClass?.getMethod("getVendorId")
                        val vendorId = getVendorId?.invoke(device) as? Int
                        vendorId == CUSTOM_VENDOR_ID
                    } catch (e: Exception) {
                        false
                    }
                }

                if (customDevice != null) {
                    Log.i(TAG, "✓ Found Custom TG2480HIII device via Custom API!")

                    // Try to connect using Custom API
                    customPrnDevice = customAPI?.javaClass
                        ?.getMethod("getPrinterDriverUSB", customDevice.javaClass, Context::class.java)
                        ?.invoke(customAPI, customDevice, context)

                    if (customPrnDevice != null) {
                        Log.i(TAG, "✓ Custom API USB connection established!")

                        // Get firmware version
                        val firmwareVersion = try {
                            customPrnDevice?.javaClass?.getMethod("getFirmwareVersion")?.invoke(customPrnDevice) as? String
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not get firmware version", e)
                            "Unknown"
                        }

                        Log.i(TAG, "Printer connected via Custom API USB. Firmware: $firmwareVersion")

                        _connectionState.value = ConnectionState.CONNECTED
                        _activeProtocolFlow.value = ConnectionProtocol.CUSTOM_USB_API
                        activeProtocol = ConnectionProtocol.CUSTOM_USB_API
                        reconnectAttempts = 0

                        return@withContext true
                    }
                } else {
                    Log.w(TAG, "Custom TG2480HIII device not found in Custom API enumeration")
                }
            } else {
                Log.w(TAG, "Custom API enumeration returned no devices or failed")
            }

            // Fallback to native Android USB enumeration if Custom API didn't work
            Log.d(TAG, "Falling back to native Android USB enumeration...")
            return@withContext tryNativeUSBConnection()

        } catch (e: CustomException) {
            Log.e(TAG, "Custom API connection failed: ${e.message}")
            Log.e(TAG, "Custom API error code: ${e.javaClass.getMethod("GetErrorCode").invoke(e)}")
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during Custom API connection", e)
            return@withContext false
        }
    }

    /**
     * Fallback to native Android USB enumeration
     */
    private suspend fun tryNativeUSBConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "--- Native Android USB Connection Attempt ---")

            val deviceList = usbManager.deviceList
            Log.d(TAG, "Native Android USB found ${deviceList.size} devices:")

            // Log all USB devices for debugging
            deviceList.values.forEachIndexed { index, device ->
                Log.d(TAG, "  USB Device $index: ${device.deviceName} " +
                        "(Vendor: ${device.vendorId}, Product: ${device.productId}, " +
                        "Class: ${device.deviceClass}, Subclass: ${device.deviceSubclass})")
            }

            // Find Custom device by vendor ID
            val customDevice = deviceList.values.find { device ->
                device.vendorId == CUSTOM_VENDOR_ID
            }

            if (customDevice != null) {
                Log.i(TAG, "✓ Found Custom device via native enumeration: ${customDevice.deviceName}")

                if (usbManager.hasPermission(customDevice)) {
                    return@withContext connectViaCustomAPI(customDevice)
                } else {
                    Log.d(TAG, "Requesting USB permission for native device...")
                    requestUsbPermission(customDevice)
                    return@withContext false // Will continue in permission callback
                }
            } else {
                Log.w(TAG, "Custom TG2480HIII device not found in native USB enumeration")
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Native USB connection attempt failed", e)
            return@withContext false
        }
    }

    /**
     * Try RS232 serial connection as fallback
     */
    private suspend fun tryRS232Connection(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "--- RS232 Serial Connection Attempt ---")

            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

            Log.d(TAG, "Found ${availableDrivers.size} USB serial drivers:")
            availableDrivers.forEachIndexed { index, driver ->
                val device = driver.device
                Log.d(TAG, "  Serial Driver $index: ${device.deviceName} " +
                        "(Vendor: ${device.vendorId}, Product: ${device.productId}, " +
                        "Ports: ${driver.ports.size})")
            }

            if (availableDrivers.isEmpty()) {
                Log.w(TAG, "No USB serial drivers found for RS232 connection")
                return@withContext false
            }

            // Try each available serial driver
            for ((index, driver) in availableDrivers.withIndex()) {
                try {
                    Log.d(TAG, "Attempting RS232 connection via driver $index...")

                    val device = driver.device
                    val connection = usbManager.openDevice(device)

                    if (connection == null) {
                        Log.w(TAG, "Failed to open USB device for serial driver $index")
                        continue
                    }

                    val port = driver.ports.firstOrNull()
                    if (port == null) {
                        connection.close()
                        Log.w(TAG, "No ports available on serial driver $index")
                        continue
                    }

                    // Configure RS232 parameters (from TG2480HIII datasheet)
                    port.open(connection)
                    port.setParameters(
                        RS232_BAUD_RATE,
                        RS232_DATA_BITS,
                        UsbSerialPort.STOPBITS_1,
                        RS232_PARITY
                    )
                    port.dtr = true
                    port.rts = true

                    Log.i(TAG, "✓ RS232 serial connection established!")
                    Log.i(TAG, "Serial parameters: ${RS232_BAUD_RATE} baud, ${RS232_DATA_BITS}N1, DTR/RTS enabled")

                    serialPort = port
                    _connectionState.value = ConnectionState.CONNECTED
                    _activeProtocolFlow.value = ConnectionProtocol.RS232_SERIAL
                    activeProtocol = ConnectionProtocol.RS232_SERIAL
                    reconnectAttempts = 0

                    return@withContext true

                } catch (e: Exception) {
                    Log.w(TAG, "RS232 connection failed for driver $index: ${e.message}")
                    continue
                }
            }

            Log.w(TAG, "All RS232 connection attempts failed")
            return@withContext false

        } catch (e: Exception) {
            Log.e(TAG, "RS232 connection attempt failed with exception", e)
            return@withContext false
        }
    }

    private suspend fun connectViaCustomAPI(device: UsbDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Connecting to Custom TG2480HIII via Custom API...")

            customPrnDevice = customAPI?.javaClass
                ?.getMethod("getPrinterDriverUSB", UsbDevice::class.java, Context::class.java)
                ?.invoke(customAPI, device, context)
                ?: throw CustomException("Failed to create Custom printer driver")

            // Get firmware version
            val firmwareVersion = try {
                customPrnDevice?.javaClass?.getMethod("getFirmwareVersion")?.invoke(customPrnDevice) as? String
            } catch (e: Exception) {
                Log.w(TAG, "Could not get firmware version", e)
                "Unknown"
            }

            Log.i(TAG, "✓ Custom printer connected via USB API. Firmware: $firmwareVersion")

            _connectionState.value = ConnectionState.CONNECTED
            _activeProtocolFlow.value = ConnectionProtocol.CUSTOM_USB_API
            activeProtocol = ConnectionProtocol.CUSTOM_USB_API
            reconnectAttempts = 0

            return@withContext true

        } catch (e: CustomException) {
            Log.e(TAG, "Custom API USB connection failed: ${e.message}")
            Log.e(TAG, "Custom API error code: ${e.javaClass.getMethod("GetErrorCode").invoke(e)}")
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected USB connection error", e)
            return@withContext false
        }
    }

    /**
     * Print text using active connection protocol
     */
    suspend fun printText(
        text: String,
        fontSize: Int = 1,
        bold: Boolean = false,
        centered: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (simulate) {
                Log.d(TAG, "SIMULATION: Print text: '$text'")
                delay(200)
                return@withContext Result.success(true)
            }

            ensureConnected()

            printerMutex.withLock {
                return@withContext when (activeProtocol) {
                    ConnectionProtocol.CUSTOM_USB_API -> printViaCustomAPI(text, fontSize, bold, centered)
                    ConnectionProtocol.RS232_SERIAL -> printViaRS232(text, fontSize, bold, centered)
                    else -> Result.failure(Exception("No active connection protocol"))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Print text failed", e)
            return@withContext Result.failure(e)
        }
    }

    private fun printViaCustomAPI(text: String, fontSize: Int, bold: Boolean, centered: Boolean): Result<Boolean> {
        try {
            Log.d(TAG, "Printing via Custom API: '$text'")

            // Create PrinterFont object
            val printerFont = PrinterFont().apply {
                when (fontSize) {
                    1 -> {
                        setCharHeight(PrinterFont.FONT_SIZE_X1)
                        setCharWidth(PrinterFont.FONT_SIZE_X1)
                    }
                    2 -> {
                        setCharHeight(PrinterFont.FONT_SIZE_X2)
                        setCharWidth(PrinterFont.FONT_SIZE_X2)
                    }
                    else -> {
                        setCharHeight(PrinterFont.FONT_SIZE_X1)
                        setCharWidth(PrinterFont.FONT_SIZE_X1)
                    }
                }

                //if (bold) setEmphasis(true)

                if (centered) {
                    setJustification(PrinterFont.FONT_JUSTIFICATION_CENTER)
                } else {
                    setJustification(PrinterFont.FONT_JUSTIFICATION_LEFT)
                }
            }

            // Print using Custom API
            customPrnDevice?.javaClass?.getMethod("printText", String::class.java, Int::class.java, Int::class.java, PrinterFont::class.java)
                ?.invoke(customPrnDevice, text, 104, 510, printerFont)

            Log.d(TAG, "✓ Custom API print successful")
            return Result.success(true)

        } catch (e: Exception) {
            Log.e(TAG, "Custom API print failed", e)
            return Result.failure(e)
        }
    }

    private fun printViaRS232(text: String, fontSize: Int, bold: Boolean, centered: Boolean): Result<Boolean> {
        try {
            Log.d(TAG, "Printing via RS232: '$text'")

            val port = serialPort ?: throw Exception("RS232 port not available")

            // Build ESC/POS command sequence
            val commands = mutableListOf<Byte>()

            // Initialize printer
            commands.addAll(byteArrayOf(0x1B, 0x40).toList()) // ESC @

            // Set font size
            if (fontSize > 1) {
                commands.addAll(byteArrayOf(0x1D, 0x21, (fontSize - 1).toByte()).toList()) // GS ! n
            }

            // Set bold
            if (bold) {
                commands.addAll(byteArrayOf(0x1B, 0x45, 0x01).toList()) // ESC E 1
            }

            // Set justification
            if (centered) {
                commands.addAll(byteArrayOf(0x1B, 0x61, 0x01).toList()) // ESC a 1
            }

            // Add text
            commands.addAll(text.toByteArray(Charsets.UTF_8).toList())

            // Line feed and cut
            commands.addAll(byteArrayOf(0x0A, 0x0D).toList()) // LF CR

            // Reset formatting
            if (bold) commands.addAll(byteArrayOf(0x1B, 0x45, 0x00).toList()) // ESC E 0
            if (centered) commands.addAll(byteArrayOf(0x1B, 0x61, 0x00).toList()) // ESC a 0

            // Send to printer
            val commandArray = commands.toByteArray()
            port.write(commandArray, 1000)

            Log.d(TAG, "✓ RS232 print successful (sent ${commandArray.size} bytes)")
            return Result.success(true)

        } catch (e: Exception) {
            Log.e(TAG, "RS232 print failed", e)
            return Result.failure(e)
        }
    }

    private suspend fun ensureConnected() {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            throw Exception("Printer not connected (state: ${_connectionState.value})")
        }
        when (activeProtocol) {
            ConnectionProtocol.CUSTOM_USB_API -> {
                if (customPrnDevice == null) {
                    throw Exception("Custom API printer device not initialized")
                }
            }
            ConnectionProtocol.RS232_SERIAL -> {
                if (serialPort == null) {
                    throw Exception("RS232 serial port not initialized")
                }
            }
            ConnectionProtocol.SIMULATION -> {
                // Simulation is always "connected"
            }
            else -> {
                throw Exception("No active connection protocol")
            }
        }
    }

    private fun createSimulatedStatus(): CustomPrinterStatus {
        return CustomPrinterStatus(
            noPaper = false,
            paperRolling = false,
            lfPressed = false,
            printerName = "Simulated TG2480HIII",
            printerInfo = "Simulation Mode",
            firmwareVersion = "SIM_1.0",
            isReady = true,
            protocol = ConnectionProtocol.SIMULATION
        )
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerUsbReceiver() {
        val filter = IntentFilter(USB_PERMISSION_ACTION)

        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.registerReceiver(
                context,
                usbReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
    }

    private fun requestUsbPermission(device: UsbDevice) {
        Log.d(TAG, "Requesting USB permission for device: ${device.deviceName}")

        val permissionIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(USB_PERMISSION_ACTION),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        usbManager.requestPermission(device, permissionIntent)
    }

    private fun startStatusMonitoring() {
        statusHandler = Handler(Looper.getMainLooper())
        statusRunnable = object : Runnable {
            override fun run() {
                driverScope.launch {
                    try {
                        checkCurrentStatus()
                    } catch (e: Exception) {
                        Log.w(TAG, "Background status check failed", e)
                    }

                    statusRunnable?.let { r ->
                        statusHandler?.postDelayed(r, STATUS_CHECK_INTERVAL)
                    }
                }
            }
        }

        statusRunnable?.let { r ->
            statusHandler?.post(r)
        }
    }

    private suspend fun checkCurrentStatus() {
        printerMutex.withLock {
            try {
                val status = when (activeProtocol) {
                    ConnectionProtocol.CUSTOM_USB_API -> getStatusViaCustomAPI()
                    ConnectionProtocol.RS232_SERIAL -> getStatusViaRS232()
                    ConnectionProtocol.SIMULATION -> createSimulatedStatus()
                    else -> null
                }

                _printerStatus.value = status

            } catch (e: Exception) {
                Log.w(TAG, "Status check failed for protocol $activeProtocol", e)
            }
        }
    }

    private fun getStatusViaCustomAPI(): CustomPrinterStatus? {
        return try {
            val printerStatus = customPrnDevice?.javaClass?.getMethod("getPrinterStatus")?.invoke(customPrnDevice) as? PrinterStatus

            if (printerStatus != null) {
                val printerName = customPrnDevice?.javaClass?.getMethod("getPrinterName")?.invoke(customPrnDevice) as? String
                val printerInfo = customPrnDevice?.javaClass?.getMethod("getPrinterInfo")?.invoke(customPrnDevice) as? String
                val firmwareVersion = customPrnDevice?.javaClass?.getMethod("getFirmwareVersion")?.invoke(customPrnDevice) as? String

                CustomPrinterStatus(
                    noPaper = printerStatus.javaClass.getField("stsNOPAPER").getBoolean(printerStatus),
                    paperRolling = printerStatus.javaClass.getField("stsPAPERROLLING").getBoolean(printerStatus),
                    lfPressed = printerStatus.javaClass.getField("stsLFPRESSED").getBoolean(printerStatus),
                    printerName = printerName,
                    printerInfo = printerInfo,
                    firmwareVersion = firmwareVersion,
                    isReady = true,
                    protocol = ConnectionProtocol.CUSTOM_USB_API
                )
            } else null

        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Custom API status", e)
            null
        }
    }

    private fun getStatusViaRS232(): CustomPrinterStatus {
        return CustomPrinterStatus(
            noPaper = false, // RS232 doesn't provide real-time status easily
            paperRolling = false,
            lfPressed = false,
            printerName = "TG2480HIII via RS232",
            printerInfo = "RS232 Serial Connection",
            firmwareVersion = "Unknown (RS232)",
            isReady = serialPort != null,
            protocol = ConnectionProtocol.RS232_SERIAL
        )
    }

    private fun acquireWakeLock() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PudoKiosk:PrinterDriver"
        )
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes
    }

    fun cleanup() {
        try {
            reconnectJob?.cancel()
            statusRunnable = null
            statusHandler = null

            runBlocking {
                printerMutex.withLock {
                    customPrnDevice?.javaClass?.getMethod("close")?.invoke(customPrnDevice)
                    customPrnDevice = null

                    serialPort?.close()
                    serialPort = null
                }
            }

            context.unregisterReceiver(usbReceiver)
            wakeLock?.release()

            _connectionState.value = ConnectionState.DISCONNECTED
            _activeProtocolFlow.value = ConnectionProtocol.NONE
            activeProtocol = ConnectionProtocol.NONE

        } catch (e: Exception) {
            Log.w(TAG, "Cleanup error", e)
        }
    }
}