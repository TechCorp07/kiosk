package com.blitztech.pudokiosk.ui

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.data.AuditLogger

// Enhanced driver imports
import com.blitztech.pudokiosk.deviceio.printer.CustomTG2480HIIIDriver
import com.hoho.android.usbserial.driver.UsbSerialProber

// Custom API imports
import it.custom.printer.api.android.CustomAndroidAPI

import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class HardwareTestActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HardwareTestActivity"
    }

    // === Enhanced Printer Components ===
    private lateinit var enhancedPrinter: CustomTG2480HIIIDriver
    private val debugger = PrinterConnectionDebugger()

    // === UI Components ===
    private lateinit var btnPrintTest: Button
    private lateinit var btnPrinterStatus: Button
    private lateinit var btnPrintReceipt: Button
    private lateinit var btnPrintBarcode: Button
    private lateinit var btnDebugConnection: Button
    private lateinit var btnProtocolSwitch: Button
    private lateinit var tvPrinterStatus: TextView
    private lateinit var tvPrinterDetails: TextView
    private lateinit var tvProtocolInfo: TextView

    // === Hardware Initialization State ===
    private var printerInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hardware_test)

        Log.i(TAG, "=== Hardware Test Activity Started ===")
        AuditLogger.log("INFO", "HARDWARE_TEST_ACTIVITY_START", "Enhanced printer driver version")

        // Set up UI elements
        setupUI()

        // Initialize enhanced printer driver with comprehensive debugging
        initializeEnhancedPrinter()
    }

    private fun setupUI() {
        // Initialize UI components
        btnPrintTest = findViewById(R.id.btnPrintTest)
        btnPrinterStatus = findViewById(R.id.btnPrinterStatus)
        btnPrintReceipt = findViewById(R.id.btnPrintReceipt)
        btnPrintBarcode = findViewById(R.id.btnPrintBarcode)

        // Add new debug buttons (you may need to add these to your layout)
        try {
            btnDebugConnection = findViewById(R.id.btnDebugConnection)
            btnProtocolSwitch = findViewById(R.id.btnProtocolSwitch)
            tvProtocolInfo = findViewById(R.id.tvProtocolInfo)
        } catch (e: Exception) {
            Log.w(TAG, "Some debug UI elements not found in layout, that's okay")
        }

        tvPrinterStatus = findViewById(R.id.tvPrinterStatus)
        tvPrinterDetails = findViewById(R.id.tvPrinterDetails)

        // Set up button click handlers
        btnPrintTest.setOnClickListener { testBasicPrinting() }
        btnPrinterStatus.setOnClickListener { checkPrinterStatus() }
        btnPrintReceipt.setOnClickListener { testReceiptPrinting() }
        btnPrintBarcode.setOnClickListener { testBarcodePrinting() }

        // Debug button handlers (if available)
        try {
            btnDebugConnection?.setOnClickListener { runComprehensiveDebug() }
            btnProtocolSwitch?.setOnClickListener { testProtocolSwitching() }
        } catch (e: Exception) {
            // Debug buttons not in layout, that's fine
        }

        // Initially disable buttons until printer is ready
        setButtonsEnabled(false)
    }

    /**
     * Initialize Enhanced TG2480HIII Driver with comprehensive debugging
     * This replaces your old initialization method and provides much better debugging
     */
    private fun initializeEnhancedPrinter() {
        showPrinterStatus("🔍 Starting comprehensive printer initialization...")

        lifecycleScope.launch {
            try {
                // Run comprehensive debug and initialize enhanced driver
                enhancedPrinter = debugger.debugPrinterConnection(this@HardwareTestActivity)

                // Monitor connection state changes
                monitorConnectionState()

                // Monitor active protocol changes
                monitorProtocolChanges()

                // Monitor detailed printer status
                monitorPrinterStatus()

                printerInitialized = true

                // Test printing after successful connection
                delay(2000)
                if (enhancedPrinter.connectionState.value == CustomTG2480HIIIDriver.ConnectionState.CONNECTED) {
                    debugger.testPrintingWithDebug(enhancedPrinter)
                    setButtonsEnabled(true)
                }

                AuditLogger.log("INFO", "ENHANCED_PRINTER_INIT_SUCCESS", "All protocols tested")

            } catch (e: Exception) {
                Log.e(TAG, "Enhanced printer initialization failed", e)
                showPrinterStatus("❌ Enhanced printer initialization failed: ${e.message}")
                AuditLogger.log("ERROR", "ENHANCED_PRINTER_INIT_FAIL", "msg=${e.message}")
            }
        }
    }

    private fun monitorConnectionState() {
        lifecycleScope.launch {
            enhancedPrinter.connectionState.collect { state ->
                val stateMessage = when (state) {
                    CustomTG2480HIIIDriver.ConnectionState.DISCONNECTED -> "⚪ Printer disconnected"
                    CustomTG2480HIIIDriver.ConnectionState.CONNECTING -> "🟡 Connecting to printer..."
                    CustomTG2480HIIIDriver.ConnectionState.CONNECTED -> "🟢 Printer connected and ready"
                    CustomTG2480HIIIDriver.ConnectionState.ERROR -> "🔴 Printer connection error"
                    CustomTG2480HIIIDriver.ConnectionState.PERMISSION_DENIED -> "🔴 USB permission denied"
                    CustomTG2480HIIIDriver.ConnectionState.DEVICE_NOT_FOUND -> "🔴 Printer not found via any protocol"
                    CustomTG2480HIIIDriver.ConnectionState.RECONNECTING -> "🟡 Reconnecting..."
                }

                withContext(Dispatchers.Main) {
                    showPrinterStatus(stateMessage)
                    setButtonsEnabled(state == CustomTG2480HIIIDriver.ConnectionState.CONNECTED)
                }

                AuditLogger.log("INFO", "ENHANCED_PRINTER_STATE_CHANGE", "state=$state")
            }
        }
    }

    private fun monitorProtocolChanges() {
        lifecycleScope.launch {
            enhancedPrinter.currentProtocol.collect { protocol ->
                val protocolMessage = when (protocol) {
                    CustomTG2480HIIIDriver.ConnectionProtocol.CUSTOM_USB_API -> {
                        "🔗 Protocol: Custom USB API (PREFERRED)"
                    }
                    CustomTG2480HIIIDriver.ConnectionProtocol.RS232_SERIAL -> {
                        "🔗 Protocol: RS232 Serial (FALLBACK)"
                    }
                    CustomTG2480HIIIDriver.ConnectionProtocol.SIMULATION -> {
                        "🔗 Protocol: Simulation Mode"
                    }
                    else -> {
                        "🔗 Protocol: None Active"
                    }
                }

                withContext(Dispatchers.Main) {
                    try {
                        tvProtocolInfo?.text = protocolMessage
                    } catch (e: Exception) {
                        // Protocol info view not in layout
                        Log.d(TAG, "Protocol: $protocol")
                    }
                }

                Log.i(TAG, "Active Protocol Changed: $protocol")
                AuditLogger.log("INFO", "PROTOCOL_CHANGE", "protocol=$protocol")
            }
        }
    }

    private fun monitorPrinterStatus() {
        lifecycleScope.launch {
            enhancedPrinter.printerStatus.collect { status ->
                status?.let {
                    withContext(Dispatchers.Main) {
                        updatePrinterDetails(it)
                    }
                }
            }
        }
    }

    private fun testBasicPrinting() {
        if (!printerInitialized) {
            showToast("⚠️ Printer not initialized yet")
            return
        }

        lifecycleScope.launch {
            try {
                showPrinterStatus("🖨️ Testing basic printing...")

                val currentProtocol = enhancedPrinter.currentProtocol.value
                val testText = buildString {
                    appendLine("=== ENHANCED DRIVER TEST ===")
                    appendLine()
                    appendLine("Protocol: $currentProtocol")
                    appendLine("Date: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}")
                    appendLine("Time: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}")
                    appendLine()
                    appendLine("Status: Print test successful!")
                    appendLine()
                    appendLine("========================")
                    appendLine()
                }

                val result = enhancedPrinter.printText(
                    text = testText,
                    fontSize = 1,
                    bold = false,
                    centered = false
                )

                if (result.isSuccess) {
                    showPrinterStatus("✅ Print test successful via $currentProtocol")
                    showToast("✅ Print test completed!")
                    AuditLogger.log("INFO", "PRINT_TEST_SUCCESS", "protocol=$currentProtocol")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    showPrinterStatus("❌ Print test failed: $error")
                    showToast("❌ Print test failed!")
                    AuditLogger.log("ERROR", "PRINT_TEST_FAIL", "msg=$error")
                }

            } catch (e: Exception) {
                showPrinterStatus("❌ Print test error: ${e.message}")
                showToast("❌ Print test error!")
                AuditLogger.log("ERROR", "PRINT_TEST_EXCEPTION", "msg=${e.message}")
            }
        }
    }

    private fun checkPrinterStatus() {
        if (!printerInitialized) {
            showToast("⚠️ Printer not initialized yet")
            return
        }

        lifecycleScope.launch {
            try {
                showPrinterStatus("🔍 Checking printer status...")

                val connectionState = enhancedPrinter.connectionState.value
                val protocol = enhancedPrinter.currentProtocol.value
                val status = enhancedPrinter.printerStatus.value

                val statusReport = buildString {
                    appendLine("=== PRINTER STATUS REPORT ===")
                    appendLine()
                    appendLine("Connection: $connectionState")
                    appendLine("Protocol: $protocol")
                    appendLine()
                    if (status != null) {
                        appendLine("Ready: ${if (status.isReady) "YES" else "NO"}")
                        appendLine("Paper: ${if (!status.noPaper) "OK" else "EMPTY"}")
                        appendLine("Paper Rolling: ${if (status.paperRolling) "YES" else "NO"}")
                        appendLine("LF Pressed: ${if (status.lfPressed) "YES" else "NO"}")
                        appendLine("Operational: ${if (status.isOperational) "YES" else "NO"}")
                        appendLine()
                        appendLine("Printer: ${status.printerName ?: "Unknown"}")
                        appendLine("Info: ${status.printerInfo ?: "Unknown"}")
                        appendLine("Firmware: ${status.firmwareVersion ?: "Unknown"}")
                    } else {
                        appendLine("Status: Information unavailable")
                    }
                    appendLine()
                    appendLine("========================")
                }

                showToast("📊 Status check completed")
                Log.i(TAG, statusReport)
                AuditLogger.log("INFO", "STATUS_CHECK", "connection=$connectionState,protocol=$protocol")

            } catch (e: Exception) {
                showPrinterStatus("❌ Status check failed: ${e.message}")
                showToast("❌ Status check failed!")
                AuditLogger.log("ERROR", "STATUS_CHECK_EXCEPTION", "msg=${e.message}")
            }
        }
    }

    private fun testReceiptPrinting() {
        if (!printerInitialized) {
            showToast("⚠️ Printer not initialized yet")
            return
        }

        lifecycleScope.launch {
            try {
                showPrinterStatus("🧾 Printing sample receipt...")

                val currentProtocol = enhancedPrinter.currentProtocol.value
                val receiptText = buildString {
                    appendLine("    PUDO KIOSK RECEIPT")
                    appendLine("    Enhanced Driver Test")
                    appendLine("=" * 32)
                    appendLine()
                    appendLine("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                    appendLine("Protocol: $currentProtocol")
                    appendLine("Transaction: TXN-${System.currentTimeMillis()}")
                    appendLine()
                    appendLine("Items:")
                    appendLine("  Test Item 1      $10.00")
                    appendLine("  Test Item 2       $5.50")
                    appendLine("  Test Item 3       $2.25")
                    appendLine("-" * 32)
                    appendLine("  Total:          $17.75")
                    appendLine()
                    appendLine("Payment: Card ****1234")
                    appendLine("Status: APPROVED")
                    appendLine()
                    appendLine("Thank you for using PUDO Kiosk!")
                    appendLine("Enhanced Driver v2.0")
                    appendLine("=" * 32)
                    appendLine()
                }

                val result = enhancedPrinter.printText(
                    text = receiptText,
                    fontSize = 1,
                    bold = false,
                    centered = false
                )

                if (result.isSuccess) {
                    showPrinterStatus("✅ Receipt printed successfully via $currentProtocol")
                    showToast("✅ Receipt printed!")
                    AuditLogger.log("INFO", "RECEIPT_PRINT_SUCCESS", "protocol=$currentProtocol")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    showPrinterStatus("❌ Receipt printing failed: $error")
                    showToast("❌ Receipt printing failed!")
                    AuditLogger.log("ERROR", "RECEIPT_PRINT_FAIL", "msg=$error")
                }

            } catch (e: Exception) {
                showPrinterStatus("❌ Receipt printing error: ${e.message}")
                showToast("❌ Receipt printing error!")
                AuditLogger.log("ERROR", "RECEIPT_PRINT_EXCEPTION", "msg=${e.message}")
            }
        }
    }

    private fun testBarcodePrinting() {
        if (!printerInitialized) {
            showToast("⚠️ Printer not initialized yet")
            return
        }

        lifecycleScope.launch {
            try {
                showPrinterStatus("📊 Printing barcode test...")

                val barcodeText = buildString {
                    appendLine("=== BARCODE TEST ===")
                    appendLine()
                    appendLine("Code 128: 123456789012")
                    appendLine("EAN-13: 1234567890123")
                    appendLine("QR Code: PUDO-KIOSK-TEST")
                    appendLine()
                    appendLine("Note: Actual barcode printing")
                    appendLine("requires specific ESC/POS commands")
                    appendLine("or Custom API barcode methods.")
                    appendLine()
                    appendLine("This is a text representation.")
                    appendLine("================")
                    appendLine()
                }

                val result = enhancedPrinter.printText(
                    text = barcodeText,
                    fontSize = 1,
                    bold = true,
                    centered = true
                )

                if (result.isSuccess) {
                    showPrinterStatus("✅ Barcode test printed successfully")
                    showToast("✅ Barcode test completed!")
                } else {
                    showPrinterStatus("❌ Barcode test failed")
                    showToast("❌ Barcode test failed!")
                }

            } catch (e: Exception) {
                showPrinterStatus("❌ Barcode test error: ${e.message}")
                showToast("❌ Barcode test error!")
            }
        }
    }

    private fun runComprehensiveDebug() {
        lifecycleScope.launch {
            try {
                showPrinterStatus("🔍 Running comprehensive debug...")

                // Re-run the full debug suite
                debugger.debugPrinterConnection(this@HardwareTestActivity)

                showToast("🔍 Debug completed - check logs!")

            } catch (e: Exception) {
                showToast("❌ Debug failed: ${e.message}")
            }
        }
    }

    private fun testProtocolSwitching() {
        lifecycleScope.launch {
            try {
                showPrinterStatus("🔄 Testing protocol capabilities...")

                val currentProtocol = enhancedPrinter.currentProtocol.value

                showToast("Current protocol: $currentProtocol")
                Log.i(TAG, "Protocol switching test - Current: $currentProtocol")

                // Note: In a production environment, you might want to implement
                // actual protocol switching logic here

            } catch (e: Exception) {
                showToast("❌ Protocol test failed: ${e.message}")
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        runOnUiThread {
            btnPrintTest.isEnabled = enabled
            btnPrinterStatus.isEnabled = enabled
            btnPrintReceipt.isEnabled = enabled
            btnPrintBarcode.isEnabled = enabled

            try {
                btnDebugConnection?.isEnabled = true // Always enabled
                btnProtocolSwitch?.isEnabled = enabled
            } catch (e: Exception) {
                // Debug buttons not in layout
            }
        }
    }

    /**
     * Update printer status display in real-time
     */
    private fun showPrinterStatus(message: String) {
        runOnUiThread {
            tvPrinterStatus.text = message
            Log.d(TAG, "Status: $message")
        }
    }

    /**
     * Update detailed printer information display
     */
    private fun updatePrinterDetails(status: CustomTG2480HIIIDriver.CustomPrinterStatus) {
        val details = buildString {
            append("Ready: ${if (status.isReady) "✓" else "✗"} | ")
            append("Paper: ${if (!status.noPaper) "✓" else "✗"} | ")
            append("Rolling: ${if (status.paperRolling) "↻" else "○"} | ")
            append("LF: ${if (status.lfPressed) "✓" else "✗"} | ")
            append("Protocol: ${status.protocol.name}")

            if (!status.isOperational) append(" | ⚠️ CHECK REQUIRED")
        }

        runOnUiThread {
            tvPrinterDetails.text = details
        }
    }

    /**
     * Display toast messages safely on main thread
     */
    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this@HardwareTestActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * String repetition operator for formatting
     */
    private operator fun String.times(count: Int): String {
        return this.repeat(count)
    }

    /**
     * Clean up all resources when activity is destroyed
     */
    override fun onDestroy() {
        super.onDestroy()

        Log.i(TAG, "=== Hardware Test Activity Destroying ===")

        try {
            if (printerInitialized) {
                enhancedPrinter.cleanup()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Printer cleanup error", e)
        }

        AuditLogger.log("INFO", "HARDWARE_TEST_ACTIVITY_DESTROYED", "Enhanced driver cleaned up")
    }

    /**
     * Enhanced Printer Connection Debugger Class
     * This provides comprehensive debugging of all connection methods
     */
    inner class PrinterConnectionDebugger {

        private val debugTag = "PrinterDebugger"

        suspend fun debugPrinterConnection(context: Context): CustomTG2480HIIIDriver {
            Log.i(debugTag, "🔍 ========== COMPREHENSIVE PRINTER DEBUG SESSION ==========")

            // Step 1: Check Android Manifest USB configuration
            debugUSBManifestConfiguration(context)

            // Step 2: Enumerate all USB devices
            debugAllUSBDevices(context)

            // Step 3: Check Custom API availability
            debugCustomAPIAvailability()

            // Step 4: Check serial port availability
            debugSerialPortAvailability(context)

            // Step 5: Initialize enhanced driver with full logging
            Log.i(debugTag, "🚀 Initializing Enhanced TG2480HIII Driver...")
            val enhancedDriver = CustomTG2480HIIIDriver(
                context = context,
                simulate = false, // Set to true for testing without hardware
                enableAutoReconnect = true,
                enableRS232Fallback = true
            )

            val result = enhancedDriver.initialize()

            if (result.isSuccess) {
                Log.i(debugTag, "✅ Enhanced driver initialization SUCCESSFUL!")
            } else {
                Log.e(debugTag, "❌ Enhanced driver initialization FAILED: ${result.exceptionOrNull()?.message}")
            }

            Log.i(debugTag, "🔍 ========== PRINTER DEBUG SESSION COMPLETE ==========")

            return enhancedDriver
        }

        private fun debugUSBManifestConfiguration(context: Context) {
            Log.d(debugTag, "--- USB Manifest Configuration Check ---")

            try {
                val packageInfo = context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA
                )

                Log.d(debugTag, "Package: ${packageInfo.packageName}")
                Log.d(debugTag, "Target SDK: ${packageInfo.applicationInfo?.targetSdkVersion}")

                // Check if USB permissions are declared
                val requestedPermissions = packageInfo.requestedPermissions
                val hasUSBPermission = requestedPermissions?.contains("android.permission.USB_PERMISSION") == true
                Log.d(debugTag, "USB Permission declared: $hasUSBPermission")

            } catch (e: Exception) {
                Log.w(debugTag, "Could not check manifest configuration", e)
            }
        }

        private fun debugAllUSBDevices(context: Context) {
            Log.d(debugTag, "--- All USB Devices Debug ---")

            try {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val deviceList = usbManager.deviceList

                Log.i(debugTag, "Total USB devices found: ${deviceList.size}")

                if (deviceList.isEmpty()) {
                    Log.w(debugTag, "⚠️ No USB devices found! Check physical connections.")
                    return
                }

                deviceList.values.forEachIndexed { index, device ->
                    Log.d(debugTag, "📱 USB Device $index:")
                    Log.d(debugTag, "  Name: ${device.deviceName}")
                    Log.d(debugTag, "  Vendor ID: ${device.vendorId} (0x${device.vendorId.toString(16).uppercase()})")
                    Log.d(debugTag, "  Product ID: ${device.productId} (0x${device.productId.toString(16).uppercase()})")
                    Log.d(debugTag, "  Device Class: ${device.deviceClass}")
                    Log.d(debugTag, "  Has Permission: ${usbManager.hasPermission(device)}")

                    // Check if this is our Custom printer
                    if (device.vendorId == 3540) {
                        Log.i(debugTag, "  🎯 THIS IS THE CUSTOM TG2480HIII PRINTER!")
                        Log.i(debugTag, "  📋 Printer Details:")
                        Log.i(debugTag, "    - Expected Vendor ID: 3540 ✅")
                        Log.i(debugTag, "    - Permission Status: ${if (usbManager.hasPermission(device)) "✅ GRANTED" else "❌ NOT GRANTED"}")
                    }

                    Log.d(debugTag, "  " + "-".repeat(40))
                }

            } catch (e: Exception) {
                Log.e(debugTag, "USB device enumeration failed", e)
            }
        }

        private fun debugCustomAPIAvailability() {
            Log.d(debugTag, "--- Custom Android API Debug ---")

            try {
                val customAPI = CustomAndroidAPI()
                val apiVersion = CustomAndroidAPI.getAPIVersion()
                Log.i(debugTag, "✅ Custom Android API available, version: $apiVersion")

            } catch (e: Exception) {
                Log.e(debugTag, "❌ Custom Android API not available or failed to initialize", e)
            }
        }

        private fun debugSerialPortAvailability(context: Context) {
            Log.d(debugTag, "--- Serial Port (RS232) Debug ---")

            try {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

                Log.i(debugTag, "Serial drivers found: ${availableDrivers.size}")

                if (availableDrivers.isEmpty()) {
                    Log.w(debugTag, "⚠️ No USB serial drivers found for RS232 fallback")
                    return
                }

                availableDrivers.forEachIndexed { index, driver ->
                    val device = driver.device
                    Log.d(debugTag, "🔌 Serial Driver $index:")
                    Log.d(debugTag, "  Device: ${device.deviceName}")
                    Log.d(debugTag, "  Vendor ID: ${device.vendorId} (0x${device.vendorId.toString(16).uppercase()})")
                    Log.d(debugTag, "  Driver: ${driver.javaClass.simpleName}")
                    Log.d(debugTag, "  Ports: ${driver.ports.size}")
                }

            } catch (e: Exception) {
                Log.e(debugTag, "Serial port debug failed", e)
            }
        }

        suspend fun testPrintingWithDebug(driver: CustomTG2480HIIIDriver) {
            Log.i(debugTag, "🧪 TESTING PRINTER FUNCTIONALITY")

            try {
                // Wait for connection
                var attempts = 0
                while (driver.connectionState.value != CustomTG2480HIIIDriver.ConnectionState.CONNECTED && attempts < 10) {
                    Log.d(debugTag, "Waiting for connection... (attempt ${attempts + 1})")
                    delay(1000)
                    attempts++
                }

                if (driver.connectionState.value != CustomTG2480HIIIDriver.ConnectionState.CONNECTED) {
                    Log.e(debugTag, "❌ Printer not connected, cannot test printing")
                    return
                }

                val activeProtocol = driver.currentProtocol.value
                Log.i(debugTag, "🔗 Testing print via: $activeProtocol")

                // Test basic text printing
                val testResult = driver.printText(
                    text = "=== ENHANCED DRIVER TEST ===\n" +
                            "Protocol: $activeProtocol\n" +
                            "Time: ${System.currentTimeMillis()}\n" +
                            "Status: Connected\n" +
                            "=====================\n\n",
                    fontSize = 1,
                    bold = false,
                    centered = true
                )

                if (testResult.isSuccess) {
                    Log.i(debugTag, "✅ Test print SUCCESSFUL via $activeProtocol")
                } else {
                    Log.e(debugTag, "❌ Test print FAILED: ${testResult.exceptionOrNull()?.message}")
                }

            } catch (e: Exception) {
                Log.e(debugTag, "Test printing failed with exception", e)
            }
        }
    }
}