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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import androidx.core.content.ContextCompat

// Actual Custom Android API imports (based on your documentation)
import it.custom.printer.api.android.CustomAndroidAPI
import it.custom.printer.api.android.CustomException
import it.custom.printer.api.android.PrinterFont
import it.custom.printer.api.android.PrinterStatus

class CustomTG2480HIIIDriver(
    private val context: Context,
    private val simulate: Boolean = false,
    private val enableAutoReconnect: Boolean = true
) {

    companion object {
        private const val TAG = "CustomTG2480HIII"
        private const val VENDOR_ID = 3540 // Custom SpA vendor ID
        private const val STATUS_CHECK_INTERVAL = 15000L // 15 seconds
        private const val RECONNECT_DELAY = 3000L // 3 seconds
        private const val USB_PERMISSION_ACTION = "com.blitztech.pudokiosk.printer.USB_PERMISSION"
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }

    // ACTUAL Custom API objects - corrected based on demo code
    private var customAPI: CustomAndroidAPI? = null
    private var prnDevice: Any? = null // The actual printer device object returned by getPrinterDriverUSB
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    // Connection state management
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _printerStatus = MutableStateFlow<CustomPrinterStatus?>(null)
    val printerStatus: StateFlow<CustomPrinterStatus?> = _printerStatus.asStateFlow()

    // Background status monitoring (like in the demo code)
    private var statusHandler: Handler? = null
    private var statusRunnable: Runnable? = null

    // Coroutine management
    private val driverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var reconnectAttempts = 0

    // FIXED: Use coroutine-based Mutex instead of synchronized for suspend functions
    private val printerMutex = Mutex()

    // USB permission handling
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (USB_PERMISSION_ACTION == intent.action) {
                synchronized(this@CustomTG2480HIIIDriver) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                    Log.d(TAG, "USB permission received: granted=$granted, device=${device?.deviceName}")

                    if (granted && device != null && device.vendorId == VENDOR_ID) {
                        driverScope.launch {
                            connectToDevice(device)
                        }
                    } else {
                        _connectionState.value = ConnectionState.PERMISSION_DENIED
                        Log.e(TAG, "USB permission denied for Custom TG2480HIII")
                    }
                }
            }
        }
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

    // Our wrapper around the actual PrinterStatus from Custom API
    data class CustomPrinterStatus(
        val noPaper: Boolean,
        val paperRolling: Boolean,
        val lfPressed: Boolean,
        val printerName: String?,
        val printerInfo: String?,
        val firmwareVersion: String?,
        val isReady: Boolean,
        val lastUpdated: Long = System.currentTimeMillis()
    ) {
        val isOperational: Boolean
            get() = isReady && !noPaper
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerUsbReceiver() {
        val filter = IntentFilter(USB_PERMISSION_ACTION)

        if (Build.VERSION.SDK_INT >= 33) {
            // Android 13+ requires explicit exported-ness
            ContextCompat.registerReceiver(
                context,
                usbReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            // Legacy registration for older Android versions
            context.registerReceiver(usbReceiver, filter)
        }
    }

    suspend fun initialize(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Initializing Custom TG2480HIII printer driver (simulate=$simulate)")

            if (simulate) {
                _connectionState.value = ConnectionState.CONNECTED
                _printerStatus.value = createSimulatedStatus()
                Log.i(TAG, "Simulation mode: printer connected")
                return@withContext Result.success(true)
            }

            // Initialize Custom Android API (exactly like demo code)
            customAPI = CustomAndroidAPI()

            registerUsbReceiver()

            // Acquire wake lock for reliable hardware communication
            acquireWakeLock()
            val deviceFound = detectAndConnectPrinter()

            if (deviceFound) {
                startStatusMonitoring()
                Result.success(true)
            } else {
                Result.failure(Exception("Custom TG2480HIII printer not detected"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            _connectionState.value = ConnectionState.ERROR
            Result.failure(e)
        }
    }

    private suspend fun detectAndConnectPrinter(): Boolean {
        _connectionState.value = ConnectionState.CONNECTING

        val deviceList = usbManager.deviceList
        Log.d(TAG, "Scanning ${deviceList.size} USB devices for Custom TG2480HIII")

        // Find Custom device by vendor ID (like in demo)
        val customDevice = deviceList.values.find { device ->
            device.vendorId == VENDOR_ID
        }

        return if (customDevice != null) {
            Log.d(TAG, "Found Custom device: ${customDevice.deviceName}")

            if (usbManager.hasPermission(customDevice)) {
                // Already have permission, connect directly
                connectToDevice(customDevice)
                true
            } else {
                // Request permission, connection will happen in broadcast receiver
                requestUsbPermission(customDevice)
                true
            }
        } else {
            Log.w(TAG, "Custom TG2480HIII not found in USB device list")
            _connectionState.value = ConnectionState.DEVICE_NOT_FOUND
            false
        }
    }

    /**
     * Request USB permission with proper Android intent handling
     */
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

    private suspend fun connectToDevice(device: UsbDevice) {
        try {
            Log.i(TAG, "Connecting to Custom TG2480HIII via manufacturer API")

            // Use ACTUAL Custom API method from your demo code
            prnDevice = customAPI?.getPrinterDriverUSB(device, context)
                ?: throw CustomException("Failed to create Custom printer driver")

            // Get firmware version (like in demo code)
            val firmwareVersion = try {
                // This is the actual method call from your demo
                (prnDevice as? Any)?.javaClass?.getMethod("getFirmwareVersion")?.invoke(prnDevice) as? String
            } catch (e: Exception) {
                Log.w(TAG, "Could not get firmware version", e)
                null
            }

            Log.i(TAG, "Custom printer connected. Firmware: $firmwareVersion")

            _connectionState.value = ConnectionState.CONNECTED
            reconnectAttempts = 0 // Reset reconnect counter on successful connection

        } catch (e: CustomException) {
            Log.e(TAG, "Custom API connection failed: ${e.message}", e)
            Log.e(TAG, "Custom API error code: ${e.GetErrorCode()}") // Note: capital G as in demo
            _connectionState.value = ConnectionState.ERROR

            if (enableAutoReconnect && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                scheduleReconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected connection error", e)
            _connectionState.value = ConnectionState.ERROR
        }
    }

    suspend fun printText(
        text: String,
        fontSize: Int = 1,
        bold: Boolean = false,
        centered: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (simulate) {
                Log.d(TAG, "Simulating print: $text")
                delay(200)
                return@withContext Result.success(true)
            }

            ensureConnected()

            // FIXED: Use coroutine Mutex instead of synchronized
            printerMutex.withLock {
                try {
                    // Create PrinterFont object exactly like in demo code
                    val printerFont = PrinterFont().apply {
                        // Set character height and width based on fontSize
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

                        // Set bold (emphasized in Custom API terminology)
                        setEmphasized(bold)
                        setItalic(false)
                        setUnderline(false)

                        // Set justification (alignment)
                        setJustification(
                            if (centered) PrinterFont.FONT_JUSTIFICATION_CENTER
                            else PrinterFont.FONT_JUSTIFICATION_LEFT
                        )

                        // Set default international character set
                        setInternationalCharSet(PrinterFont.FONT_CS_DEFAULT)
                    }

                    // Use ACTUAL Custom API method from demo code
                    val printTextMethod = prnDevice?.javaClass?.getMethod("printTextLF", String::class.java, PrinterFont::class.java)
                    printTextMethod?.invoke(prnDevice, text, printerFont)

                    Log.d(TAG, "Printed text successfully: ${text.take(50)}...")
                    Result.success(true)

                } catch (e: CustomException) {
                    Log.e(TAG, "Custom API print failed: ${e.message}", e)
                    handleCustomException(e)
                    Result.failure(e)
                } catch (e: Exception) {
                    Log.e(TAG, "Print failed with reflection error", e)
                    Result.failure(e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Print operation failed", e)
            Result.failure(e)
        }
    }

    // FIXED: Use Mutex instead of synchronized for suspend functions
    suspend fun printReceipt(
        header: String,
        items: List<Pair<String, String>>,
        footer: String,
        cutPaper: Boolean = true
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            ensureConnected()

            // FIXED: Use coroutine Mutex instead of synchronized
            printerMutex.withLock {
                printText(header, fontSize = 2, bold = true, centered = true)
                printText("=" * 32, fontSize = 1, bold = false, centered = false)

                // Print items with normal formatting
                items.forEach { (name, price) ->
                    val line = formatReceiptLine(name, price, 32)
                    printText(line, fontSize = 1, bold = false, centered = false)
                }

                // Print footer with centering
                printText("=" * 32, fontSize = 1, bold = false, centered = false)
                printText(footer, fontSize = 1, bold = false, centered = true)

                // Feed paper and cut (using actual Custom API methods)
                if (cutPaper) {
                    try {
                        // Feed 3 lines like in demo code
                        val feedMethod = prnDevice?.javaClass?.getMethod("feed", Int::class.java)
                        feedMethod?.invoke(prnDevice, 3)

                        // Cut paper using actual Custom API constant
                        val cutMethod = prnDevice?.javaClass?.getMethod("cut", Int::class.java)
                        // Note: We need to use the actual constant value from CustomPrinter.CUT_TOTAL
                        // For now using reflection to get the constant
                        val customPrinterClass = Class.forName("it.custom.printer.api.android.CustomPrinter")
                        val cutTotalValue = customPrinterClass.getField("CUT_TOTAL").getInt(null)
                        cutMethod?.invoke(prnDevice, cutTotalValue)

                    } catch (e: CustomException) {
                        // Only show error if it's not "unsupported function" (like in demo)
                        if (e.GetErrorCode() != CustomException.ERR_UNSUPPORTEDFUNCTION.toLong()) {
                            Log.w(TAG, "Feed/Cut operation issue: ${e.message}")
                        }
                    }
                }

                Log.i(TAG, "Receipt printed successfully")
                Result.success(true)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Receipt printing failed", e)
            Result.failure(e)
        }
    }

    /**
     * Get printer status using ACTUAL Custom API
     *
     * This follows the exact pattern from your demo's GetStatusRunnable
     * FIXED: Use Mutex instead of synchronized for suspend functions
     */
    suspend fun getCurrentStatus(): Result<CustomPrinterStatus> = withContext(Dispatchers.IO) {
        try {
            if (simulate) {
                return@withContext Result.success(createSimulatedStatus())
            }

            ensureConnected()

            // FIXED: Use coroutine Mutex instead of synchronized
            printerMutex.withLock {
                try {
                    // Use ACTUAL Custom API method from demo code
                    val getStatusMethod = prnDevice?.javaClass?.getMethod("getPrinterFullStatus")
                    val printerStatus = getStatusMethod?.invoke(prnDevice) as? PrinterStatus
                        ?: throw Exception("Could not get printer status")

                    // Get printer info (like in demo)
                    val getNameMethod = prnDevice?.javaClass?.getMethod("getPrinterName")
                    val printerName = getNameMethod?.invoke(prnDevice) as? String

                    val getInfoMethod = prnDevice?.javaClass?.getMethod("getPrinterInfo")
                    val printerInfo = getInfoMethod?.invoke(prnDevice) as? String

                    val getFwMethod = prnDevice?.javaClass?.getMethod("getFirmwareVersion")
                    val firmwareVersion = getFwMethod?.invoke(prnDevice) as? String

                    // Create our wrapper status object using ACTUAL PrinterStatus properties
                    val status = CustomPrinterStatus(
                        noPaper = printerStatus.stsNOPAPER,         // Actual property from demo
                        paperRolling = printerStatus.stsPAPERROLLING, // Actual property from demo
                        lfPressed = printerStatus.stsLFPRESSED,     // Actual property from demo
                        printerName = printerName,
                        printerInfo = printerInfo,
                        firmwareVersion = firmwareVersion,
                        isReady = !printerStatus.stsNOPAPER  // Simple ready logic
                    )

                    _printerStatus.value = status
                    Result.success(status)

                } catch (e: CustomException) {
                    Log.e(TAG, "Status check failed: ${e.message}", e)
                    Result.failure(e)
                } catch (e: Exception) {
                    Log.e(TAG, "Status check error", e)
                    Result.failure(e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Status operation failed", e)
            Result.failure(e)
        }
    }

    /**
     * Start background status monitoring like in the demo code
     *
     * This replicates the Handler-based status checking from your demo
     */
    private fun startStatusMonitoring() {
        statusHandler = Handler(Looper.getMainLooper())

        statusRunnable = object : Runnable {
            override fun run() {
                // Run status check in background thread
                driverScope.launch {
                    try {
                        if (_connectionState.value == ConnectionState.CONNECTED) {
                            getCurrentStatus()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Background status check failed", e)
                    }

                    // Schedule next status check
                    statusRunnable?.let { r ->
                        statusHandler?.postDelayed(r, STATUS_CHECK_INTERVAL)
                    }
                }
            }
        }

        // Start the status monitoring
        statusRunnable?.let { r ->
            statusHandler?.post(r)
        }
    }

    private fun handleCustomException(e: CustomException) {
        when (e.GetErrorCode()) { // Note: GetErrorCode() with capital G
            CustomException.ERR_UNSUPPORTEDFUNCTION.toLong() -> {
                Log.w(TAG, "Unsupported function called")
            }
            else -> {
                Log.e(TAG, "Custom API error ${e.GetErrorCode()}: ${e.message}")
                if (enableAutoReconnect) {
                    scheduleReconnect()
                }
            }
        }
    }

    /**
     * Schedule automatic reconnection attempt
     */
    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return

        _connectionState.value = ConnectionState.RECONNECTING
        reconnectAttempts++

        Log.i(TAG, "Scheduling reconnection attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS")

        reconnectJob = driverScope.launch {
            delay(RECONNECT_DELAY)

            if (reconnectAttempts <= MAX_RECONNECT_ATTEMPTS) {
                try {
                    // FIXED: Close printer safely without synchronized block
                    printerMutex.withLock {
                        prnDevice?.javaClass?.getMethod("close")?.invoke(prnDevice)
                        prnDevice = null
                    }
                    delay(1000)
                    detectAndConnectPrinter()
                } catch (e: Exception) {
                    Log.e(TAG, "Reconnection attempt failed", e)
                    _connectionState.value = ConnectionState.ERROR
                }
            } else {
                Log.e(TAG, "Max reconnection attempts reached")
                _connectionState.value = ConnectionState.ERROR
            }
        }
    }

    /**
     * Ensure printer is connected before operations
     */
    private suspend fun ensureConnected() {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            throw Exception("Printer not connected (state: ${_connectionState.value})")
        }
        if (prnDevice == null) {
            throw Exception("Printer device not initialized")
        }
    }

    /**
     * Create simulated status for testing
     */
    private fun createSimulatedStatus(): CustomPrinterStatus {
        return CustomPrinterStatus(
            noPaper = false,
            paperRolling = false,
            lfPressed = false,
            printerName = "Simulated TG2480HIII",
            printerInfo = "Simulation Mode",
            firmwareVersion = "SIM_1.0",
            isReady = true
        )
    }

    /**
     * Format receipt line with proper spacing
     */
    private fun formatReceiptLine(name: String, price: String, totalWidth: Int): String {
        val maxNameWidth = totalWidth - price.length - 1
        val trimmedName = if (name.length > maxNameWidth) {
            name.substring(0, maxNameWidth - 3) + "..."
        } else {
            name
        }

        val spacesNeeded = totalWidth - trimmedName.length - price.length
        return trimmedName + " ".repeat(spacesNeeded.coerceAtLeast(1)) + price + "\n"
    }

    /**
     * String repetition operator
     */
    private operator fun String.times(count: Int): String = this.repeat(count)

    /**
     * Acquire wake lock for reliable operation on Android 7.1.2
     */
    private fun acquireWakeLock() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PudoKiosk::PrinterWakeLock"
        ).apply {
            acquire(TimeUnit.HOURS.toMillis(1))
        }
        Log.d(TAG, "Wake lock acquired for printer operations")
    }

    fun shutdown() {
        Log.i(TAG, "Shutting down Custom TG2480HIII driver")

        try {
            // Stop status monitoring
            statusRunnable?.let { r ->
                statusHandler?.removeCallbacks(r)
            }
            statusHandler = null
            statusRunnable = null

            // Cancel background operations
            reconnectJob?.cancel()
            driverScope.cancel()

            // Unregister USB receiver
            try {
                context.unregisterReceiver(usbReceiver)
            } catch (e: IllegalArgumentException) {
                // Receiver wasn't registered, ignore
            }

            // FIXED: Close printer safely using blocking operation in shutdown
            runBlocking {
                printerMutex.withLock {
                    try {
                        prnDevice?.javaClass?.getMethod("close")?.invoke(prnDevice)
                    } catch (e: CustomException) {
                        Log.w(TAG, "Error closing printer: ${e.message}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Error during printer close", e)
                    }
                    prnDevice = null
                }
            }

            customAPI = null

            // Release wake lock
            wakeLock?.let { wl ->
                if (wl.isHeld) {
                    wl.release()
                    Log.d(TAG, "Wake lock released")
                }
            }
            wakeLock = null

            _connectionState.value = ConnectionState.DISCONNECTED
            _printerStatus.value = null

        } catch (e: Exception) {
            Log.w(TAG, "Error during shutdown", e)
        }
    }
}