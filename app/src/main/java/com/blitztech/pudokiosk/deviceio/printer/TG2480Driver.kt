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
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
// Custom Android API imports
import it.custom.printer.api.android.CustomAndroidAPI
import it.custom.printer.api.android.PrinterFont

// ZXing imports for barcode generation
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

// Android graphics imports
import android.graphics.Bitmap
import android.graphics.Color

/**
 * Production TG2480HIII printer driver using Custom API
 * Supports text printing and barcode generation via ESC/POS commands
 */
class CustomTG2480HIIIDriver(
    private val context: Context,
    private val enableAutoReconnect: Boolean = true
) {

    companion object {
        private const val TAG = "TG2480HIII-Driver"
        private const val CUSTOM_VENDOR_ID = 3540 // Custom SpA vendor ID
        private const val STATUS_CHECK_INTERVAL = 15000L // 15 seconds
        private const val USB_PERMISSION_ACTION = "com.blitztech.pudokiosk.printer.USB_PERMISSION"
        private const val MAX_RECONNECT_ATTEMPTS = 3
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR,
        PERMISSION_DENIED,
        DEVICE_NOT_FOUND
    }

    enum class BarcodeType(val displayName: String) {
        EAN13("EAN-13"),
        CODE128("Code 128"),
        GS1_128("GS1-128"),
        QR_CODE("QR Code")
    }

    data class BarcodeConfig(
        val type: BarcodeType,
        val data: String,
        val centered: Boolean = true
    )

    data class PrinterStatus(
        val noPaper: Boolean,
        val paperRolling: Boolean,
        val lfPressed: Boolean,
        val printerName: String?,
        val printerInfo: String?,
        val firmwareVersion: String?,
        val isReady: Boolean,
        val lastUpdated: Long = System.currentTimeMillis()
    ) {
        val isOperational: Boolean get() = isReady && !noPaper
    }

    // Custom API objects
    private var customAPI: CustomAndroidAPI? = null
    private var customPrnDevice: Any? = null

    // USB Manager
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    // State management
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _printerStatus = MutableStateFlow<PrinterStatus?>(null)
    val printerStatus: StateFlow<PrinterStatus?> = _printerStatus.asStateFlow()

    // Background monitoring
    private var statusHandler: Handler? = null
    private var statusRunnable: Runnable? = null

    // Coroutines and synchronization
    private val driverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
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
                    }
                }
            }
        }
    }

    /**
     * Initialize the printer driver
     */
    suspend fun initialize(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Initializing Custom TG2480HIII Printer Driver")
            Log.i(TAG, "Auto-reconnect: $enableAutoReconnect")

            // Initialize Custom Android API
            customAPI = CustomAndroidAPI()
            Log.i(TAG, "Custom API Version: ${CustomAndroidAPI.getAPIVersion()}")

            // Register USB receiver
            registerUsbReceiver()

            // Attempt connection
            _connectionState.value = ConnectionState.CONNECTING
            val connected = attemptConnection()

            if (connected) {
                Log.i(TAG, "Printer connection successful")
                startStatusMonitoring()
                return@withContext Result.success(true)
            } else {
                Log.e(TAG, "Printer connection failed")
                _connectionState.value = ConnectionState.DEVICE_NOT_FOUND
                return@withContext Result.failure(Exception("Custom TG2480HIII printer not found"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            _connectionState.value = ConnectionState.ERROR
            return@withContext Result.failure(e)
        }
    }

    /**
     * Attempt to connect to the printer
     */
    private suspend fun attemptConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            // First try Custom API enumeration
            val customApiDevices = enumerateCustomAPIDevices()
            if (customApiDevices.isNotEmpty()) {
                val customDevice = customApiDevices.find { isCustomTG2480Device(it) }
                if (customDevice != null) {
                    return@withContext connectToCustomDevice(customDevice)
                }
            }

            // Fallback to native USB enumeration
            val nativeDevices = usbManager.deviceList.values
            val customDevice = nativeDevices.find { it.vendorId == CUSTOM_VENDOR_ID }

            if (customDevice != null) {
                if (usbManager.hasPermission(customDevice)) {
                    return@withContext connectViaCustomAPI(customDevice)
                } else {
                    requestUsbPermission(customDevice)
                    return@withContext false // Will continue in permission callback
                }
            }

            return@withContext false

        } catch (e: Exception) {
            Log.e(TAG, "Connection attempt failed", e)
            return@withContext false
        }
    }

    /**
     * Enumerate devices using Custom API
     */
    private fun enumerateCustomAPIDevices(): Array<*> {
        return try {
            customAPI?.javaClass
                ?.getMethod("EnumUsbDevices", Context::class.java)
                ?.invoke(customAPI, context) as? Array<*> ?: emptyArray<Any>()
        } catch (e: Exception) {
            Log.w(TAG, "Custom API enumeration failed", e)
            emptyArray<Any>()
        }
    }

    /**
     * Check if device is Custom TG2480 based on vendor ID
     */
    private fun isCustomTG2480Device(device: Any?): Boolean {
        return try {
            val getVendorId = device?.javaClass?.getMethod("getVendorId")
            val vendorId = getVendorId?.invoke(device) as? Int
            vendorId == CUSTOM_VENDOR_ID
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Connect to Custom API device
     */
    private suspend fun connectToCustomDevice(device: Any): Boolean = withContext(Dispatchers.IO) {
        try {
            customPrnDevice = customAPI?.javaClass
                ?.getMethod("getPrinterDriverUSB", device.javaClass, Context::class.java)
                ?.invoke(customAPI, device, context)

            if (customPrnDevice != null) {
                val firmwareVersion = getFirmwareVersion()
                Log.i(TAG, "Connected via Custom API. Firmware: $firmwareVersion")

                _connectionState.value = ConnectionState.CONNECTED
                reconnectAttempts = 0
                return@withContext true
            }

            return@withContext false

        } catch (e: Exception) {
            Log.e(TAG, "Custom API device connection failed", e)
            return@withContext false
        }
    }

    /**
     * Connect via native USB device
     */
    private suspend fun connectViaCustomAPI(device: UsbDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            customPrnDevice = customAPI?.javaClass
                ?.getMethod("getPrinterDriverUSB", UsbDevice::class.java, Context::class.java)
                ?.invoke(customAPI, device, context)

            if (customPrnDevice != null) {
                val firmwareVersion = getFirmwareVersion()
                Log.i(TAG, "Connected via USB. Firmware: $firmwareVersion")

                _connectionState.value = ConnectionState.CONNECTED
                reconnectAttempts = 0
                return@withContext true
            }

            return@withContext false

        } catch (e: Exception) {
            Log.e(TAG, "USB connection failed", e)
            return@withContext false
        }
    }

    /**
     * Print text using Custom API
     */
    suspend fun printText(
        text: String,
        fontSize: Int = 1,
        bold: Boolean = false,
        centered: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            ensureConnected()

            printerMutex.withLock {
                val device = customPrnDevice ?: throw Exception("Printer not initialized")

                Log.d(TAG, "Printing text via Custom API: '$text'")

                // Create PrinterFont object
                val printerFont = PrinterFont().apply {
                    // Set font size
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

                    // Set bold
                    if (bold) setEmphasized(true)

                    // Set alignment
                    if (centered) {
                        setJustification(PrinterFont.FONT_JUSTIFICATION_CENTER)
                    } else {
                        setJustification(PrinterFont.FONT_JUSTIFICATION_LEFT)
                    }
                }

                // Print using Custom API
                device.javaClass.getMethod("printText", String::class.java, PrinterFont::class.java)
                    .invoke(device, text, printerFont)

                Log.d(TAG, "Text printed successfully")
                return@withContext Result.success(true)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Text printing failed", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Print barcode using bitmap generation + Custom API printImage
     */
    suspend fun printBarcode(config: BarcodeConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            ensureConnected()

            printerMutex.withLock {
                val device = customPrnDevice ?: throw Exception("Printer not initialized")

                Log.d(TAG, "Generating ${config.type.displayName} barcode bitmap: '${config.data}'")

                // Validate and clean barcode data
                val validatedData = validateBarcodeData(config.type, config.data)

                // Generate barcode bitmap using ZXing
                val barcodeBitmap = generateBarcodeBitmap(config.copy(data = validatedData))
                    ?: throw Exception("Failed to generate barcode bitmap")

                Log.d(TAG, "Generated barcode bitmap: ${barcodeBitmap.width}x${barcodeBitmap.height}")

                // Print bitmap using Custom API
                val printImageMethod = device.javaClass.getMethod(
                    "printImage",
                    Bitmap::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java
                )

                // Calculate margins and width for centering
                val leftMargin = if (config.centered) 30 else 0 // Center the barcode
                val targetWidth = 200 // Reduced from 350 to make barcodes smaller
                val scaleMode = 1 // IMAGE_SCALE_TO_WIDTH (from Custom API docs)

                printImageMethod.invoke(device, barcodeBitmap, leftMargin, scaleMode, targetWidth)

                Log.d(TAG, "Barcode printed successfully via printImage")
                return@withContext Result.success(true)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Barcode printing failed", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Generate barcode bitmap using ZXing
     */
    private fun generateBarcodeBitmap(config: BarcodeConfig): Bitmap? {
        return try {
            when (config.type) {
                BarcodeType.QR_CODE -> generateQRCodeBitmap(config.data)
                BarcodeType.CODE128 -> generateLinearBarcode(config.data, BarcodeFormat.CODE_128)
                BarcodeType.EAN13 -> generateLinearBarcode(config.data, BarcodeFormat.EAN_13)
                BarcodeType.GS1_128 -> generateLinearBarcode(config.data, BarcodeFormat.CODE_128)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate ${config.type.displayName} bitmap", e)
            null
        }
    }

    /**
     * Generate QR Code bitmap
     */
    private fun generateQRCodeBitmap(data: String): Bitmap? {
        return try {
            val writer = MultiFormatWriter()
            val hints = hashMapOf<EncodeHintType, Any>()
            hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
            hints[EncodeHintType.MARGIN] = 1 // Reduced margin

            // Smaller QR code - reduced from 200x200 to 150x150
            val bitMatrix: BitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, 150, 150, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }

            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "QR code generation failed", e)
            null
        }
    }

    /**
     * Generate linear barcode bitmap (Code 128, EAN-13, etc.)
     */
    private fun generateLinearBarcode(data: String, format: BarcodeFormat): Bitmap? {
        return try {
            val writer = MultiFormatWriter()
            val hints = hashMapOf<EncodeHintType, Any>()
            hints[EncodeHintType.MARGIN] = 5 // Reduced margin

            // Smaller linear barcodes - reduced widths and height
            val width = when (format) {
                BarcodeFormat.EAN_13 -> 200      // Reduced from 300
                BarcodeFormat.CODE_128 -> 250    // Reduced from 350
                else -> 200
            }
            val height = 80 // Reduced from 100

            val bitMatrix: BitMatrix = writer.encode(data, format, width, height, hints)
            val bitmapWidth = bitMatrix.width
            val bitmapHeight = bitMatrix.height
            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.RGB_565)

            for (x in 0 until bitmapWidth) {
                for (y in 0 until bitmapHeight) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }

            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Linear barcode generation failed for format $format", e)
            null
        }
    }

    /**
     * Convenience methods for specific barcode types
     */
    suspend fun printCode128(data: String, centered: Boolean = true): Result<Boolean> {
        return printBarcode(BarcodeConfig(BarcodeType.CODE128, data, centered))
    }

    suspend fun printEAN13(data: String, centered: Boolean = true): Result<Boolean> {
        return printBarcode(BarcodeConfig(BarcodeType.EAN13, data, centered))
    }

    suspend fun printQRCode(data: String, centered: Boolean = true): Result<Boolean> {
        return printBarcode(BarcodeConfig(BarcodeType.QR_CODE, data, centered))
    }

    suspend fun printGS1128(data: String, centered: Boolean = true): Result<Boolean> {
        return printBarcode(BarcodeConfig(BarcodeType.GS1_128, data, centered))
    }

    /**
     * Print comprehensive barcode test using bitmap generation
     */
    suspend fun printBarcodeTest(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting barcode test with bitmap generation")

            // Header
            printText("=== BARCODE TEST ===\n", fontSize = 2, bold = true, centered = true)
            delay(500)

            val testResults = mutableListOf<Pair<String, Boolean>>()

            // Test Code 128
            try {
                printText("Testing Code 128...\n", centered = true)
                delay(200)
                val result = printCode128("123456789012")
                testResults.add("Code 128" to result.isSuccess)
                Log.d(TAG, "Code 128 test: ${if (result.isSuccess) "PASS" else "FAIL"}")
                delay(1000)
            } catch (e: Exception) {
                testResults.add("Code 128" to false)
                Log.e(TAG, "Code 128 test failed", e)
            }

            // Test EAN-13
            try {
                printText("Testing EAN-13...\n", centered = true)
                delay(200)
                val result = printEAN13("1234567890123")
                testResults.add("EAN-13" to result.isSuccess)
                Log.d(TAG, "EAN-13 test: ${if (result.isSuccess) "PASS" else "FAIL"}")
                delay(1000)
            } catch (e: Exception) {
                testResults.add("EAN-13" to false)
                Log.e(TAG, "EAN-13 test failed", e)
            }

            // Test QR Code
            try {
                printText("Testing QR Code...\n", centered = true)
                delay(200)
                val result = printQRCode("PUDO-KIOSK-TEST")
                testResults.add("QR Code" to result.isSuccess)
                Log.d(TAG, "QR Code test: ${if (result.isSuccess) "PASS" else "FAIL"}")
                delay(1000)
            } catch (e: Exception) {
                testResults.add("QR Code" to false)
                Log.e(TAG, "QR Code test failed", e)
            }

            // Test GS1-128
            try {
                printText("Testing GS1-128...\n", centered = true)
                delay(200)
                val result = printGS1128("123456789012")
                testResults.add("GS1-128" to result.isSuccess)
                Log.d(TAG, "GS1-128 test: ${if (result.isSuccess) "PASS" else "FAIL"}")
                delay(1000)
            } catch (e: Exception) {
                testResults.add("GS1-128" to false)
                Log.e(TAG, "GS1-128 test failed", e)
            }

            // Print results
            printText("\n=== TEST RESULTS ===\n", fontSize = 1, bold = true, centered = true)
            testResults.forEach { (name, success) ->
                val status = if (success) "PASS" else "FAIL"
                printText("$name: $status\n", centered = true)
            }

            printText("\n" + "=".repeat(30) + "\n", centered = true)

            val allSuccessful = testResults.all { it.second }
            val successCount = testResults.count { it.second }

            Log.d(TAG, "Barcode test completed: $successCount/${testResults.size} passed")

            if (allSuccessful) {
                printText("ALL TESTS PASSED!\n", fontSize = 2, bold = true, centered = true)
            } else {
                printText("Some tests failed. Check logs.\n", bold = true, centered = true)
            }

            return@withContext Result.success(allSuccessful)

        } catch (e: Exception) {
            Log.e(TAG, "Barcode test failed", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Feed and cut paper
     */
    suspend fun feedAndCut(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            ensureConnected()

            printerMutex.withLock {
                val device = customPrnDevice ?: throw Exception("Printer not initialized")

                // Feed paper
                device.javaClass.getMethod("feed", Int::class.java).invoke(device, 6)

                // Cut paper
                device.javaClass.getMethod("cut", Int::class.java).invoke(device, 0)

                // Present ticket
                device.javaClass.getMethod("present", Int::class.java).invoke(device, 80)

                Log.d(TAG, "Feed and cut completed")
                return@withContext Result.success(true)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Feed and cut failed", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Validate barcode data
     */
    private fun validateBarcodeData(type: BarcodeType, data: String): String {
        return when (type) {
            BarcodeType.EAN13 -> {
                val cleaned = data.replace(Regex("[^0-9]"), "")
                when (cleaned.length) {
                    12 -> cleaned + calculateEAN13CheckDigit(cleaned)
                    13 -> cleaned
                    else -> throw IllegalArgumentException("EAN-13 requires 12 or 13 digits, got ${cleaned.length}")
                }
            }
            BarcodeType.CODE128, BarcodeType.GS1_128 -> data // Full ASCII support
            BarcodeType.QR_CODE -> data // Any data allowed
        }
    }

    /**
     * Calculate EAN-13 check digit
     */
    private fun calculateEAN13CheckDigit(data: String): String {
        var sum = 0
        for (i in data.indices) {
            val digit = data[i].toString().toInt()
            sum += if (i % 2 == 0) digit else digit * 3
        }
        val checkDigit = (10 - (sum % 10)) % 10
        return checkDigit.toString()
    }

    /**
     * Get firmware version
     */
    private fun getFirmwareVersion(): String {
        return try {
            customPrnDevice?.javaClass?.getMethod("getFirmwareVersion")?.invoke(customPrnDevice) as? String ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Check printer status
     */
    private suspend fun checkCurrentStatus() {
        printerMutex.withLock {
            try {
                val device = customPrnDevice ?: return@withLock

                val status = device.javaClass.getMethod("getPrinterFullStatus").invoke(device) as? PrinterStatus
                val printerName = device.javaClass.getMethod("getPrinterName").invoke(device) as? String
                val printerInfo = device.javaClass.getMethod("getPrinterInfo").invoke(device) as? String
                val firmwareVersion = getFirmwareVersion()

                if (status != null) {
                    val currentStatus = PrinterStatus(
                        noPaper = status.javaClass.getField("stsNOPAPER").getBoolean(status),
                        paperRolling = status.javaClass.getField("stsPAPERROLLING").getBoolean(status),
                        lfPressed = status.javaClass.getField("stsLFPRESSED").getBoolean(status),
                        printerName = printerName,
                        printerInfo = printerInfo,
                        firmwareVersion = firmwareVersion,
                        isReady = true
                    )

                    _printerStatus.value = currentStatus
                }

            } catch (e: Exception) {
                Log.w(TAG, "Status check failed", e)
            }
        }
    }

    private fun ensureConnected() {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            throw Exception("Printer not connected (state: ${_connectionState.value})")
        }
        if (customPrnDevice == null) {
            throw Exception("Printer device not initialized")
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerUsbReceiver() {
        val filter = IntentFilter(USB_PERMISSION_ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.registerReceiver(context, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
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
                        Log.w(TAG, "Status monitoring failed", e)
                    }
                    statusRunnable?.let { r ->
                        statusHandler?.postDelayed(r, STATUS_CHECK_INTERVAL)
                    }
                }
            }
        }
        statusRunnable?.let { statusHandler?.post(it) }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        try {
            statusRunnable = null
            statusHandler = null

            runBlocking {
                printerMutex.withLock {
                    customPrnDevice?.javaClass?.getMethod("close")?.invoke(customPrnDevice)
                    customPrnDevice = null
                }
            }

            try {
                context.unregisterReceiver(usbReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering USB receiver", e)
            }

            _connectionState.value = ConnectionState.DISCONNECTED

        } catch (e: Exception) {
            Log.w(TAG, "Cleanup error", e)
        }
    }
}