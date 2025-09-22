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
import com.blitztech.pudokiosk.prefs.Prefs

// Enhanced driver imports
import com.blitztech.pudokiosk.deviceio.printer.CustomTG2480HIIIDriver
import com.hoho.android.usbserial.driver.UsbSerialProber

// Custom API imports
import it.custom.printer.api.android.CustomAndroidAPI

// Barcode scanner imports
import com.blitztech.pudokiosk.deviceio.rs232.BarcodeScanner1900
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

class HardwareTestActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HardwareTestActivity"
    }

    // === Printer Components ===
    private lateinit var enhancedPrinter: CustomTG2480HIIIDriver
    private val debugger = PrinterConnectionDebugger()

    // === Printer UI Components ===
    private lateinit var btnPrintTest: Button
    private lateinit var btnPrinterStatus: Button
    private lateinit var btnPrintReceipt: Button
    private lateinit var btnPrintBarcode: Button
    private lateinit var btnDebugConnection: Button
    private lateinit var btnProtocolSwitch: Button
    private lateinit var tvPrinterStatus: TextView
    private lateinit var tvPrinterDetails: TextView
    private lateinit var tvProtocolInfo: TextView

    // === Barcode Scanner Components ===
    private lateinit var btnScannerFocus: Button
    private lateinit var etScannerSink: EditText
    private lateinit var btnTriggerScan: Button
    private lateinit var tvScannerStatus: TextView
    private lateinit var btnTestConnection: Button
    private lateinit var btnClearScans: Button
    private lateinit var btnRunDiagnostics: Button
    private lateinit var btnHardwareHelp: Button
    private lateinit var tvConnectionInfo: TextView

    private lateinit var barcodeScanner: BarcodeScanner1900
    private lateinit var diagnostic: RS232DiagnosticUtility
    private lateinit var prefs: Prefs

    // === Hardware Initialization State ===
    private var printerInitialized = false
    private var scannerInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hardware_test)

        Log.i(TAG, "=== Hardware Test Activity Started ===")
        AuditLogger.log("INFO", "HARDWARE_TEST_ACTIVITY_START", "Enhanced printer and RS232 scanner")

        // Initialize preferences
        prefs = Prefs(this)

        // Set up UI elements
        setupUI()

        // Initialize all hardware components
        initializeEnhancedPrinter()
        setupBarcodeScanner()
    }

    private fun setupUI() {
        // Initialize printer UI components
        btnPrintTest = findViewById(R.id.btnPrintTest)
        btnPrinterStatus = findViewById(R.id.btnPrinterStatus)
        btnPrintReceipt = findViewById(R.id.btnPrintReceipt)
        btnPrintBarcode = findViewById(R.id.btnPrintBarcode)

        try {
            btnDebugConnection = findViewById(R.id.btnDebugConnection)
            btnProtocolSwitch = findViewById(R.id.btnProtocolSwitch)
            tvProtocolInfo = findViewById(R.id.tvProtocolInfo)
        } catch (e: Exception) {
            Log.w(TAG, "Some printer debug UI elements not found in layout")
        }

        tvPrinterStatus = findViewById(R.id.tvPrinterStatus)
        tvPrinterDetails = findViewById(R.id.tvPrinterDetails)

        // Initialize barcode scanner UI components
        btnScannerFocus = findViewById(R.id.btnScannerFocus)
        etScannerSink = findViewById(R.id.etScannerSink)
        tvScannerStatus = findViewById(R.id.tvScannerStatus)

        try {
            btnTriggerScan = findViewById(R.id.btnTriggerScan)
            btnTestConnection = findViewById(R.id.btnTestConnection)
            btnClearScans = findViewById(R.id.btnClearScans)
            btnRunDiagnostics = findViewById(R.id.btnRunDiagnostics)
            btnHardwareHelp = findViewById(R.id.btnHardwareHelp)
            tvConnectionInfo = findViewById(R.id.tvConnectionInfo)
        } catch (e: Exception) {
            Log.w(TAG, "Some barcode scanner UI elements not found in layout")
        }

        // Set up printer button click handlers
        btnPrintTest.setOnClickListener { testBasicPrinting() }
        btnPrinterStatus.setOnClickListener { checkPrinterStatus() }
        btnPrintReceipt.setOnClickListener { testReceiptPrinting() }
        btnPrintBarcode.setOnClickListener { testBarcodePrinting() }

        try {
            btnDebugConnection?.setOnClickListener { runComprehensiveDebug() }
            btnProtocolSwitch?.setOnClickListener { testProtocolSwitching() }
        } catch (e: Exception) {
            Log.w(TAG, "Debug buttons not available")
        }

        // Set up barcode scanner button click handlers
        btnScannerFocus.setOnClickListener {
            focusScannerInput()
            triggerScan() // Also trigger scan when focus button is pressed
        }

        btnTriggerScan?.setOnClickListener { triggerScan() }
        btnTestConnection?.setOnClickListener { testScannerConnection() }
        btnClearScans?.setOnClickListener { clearScanResults() }
        btnRunDiagnostics?.setOnClickListener { runRS232Diagnostics() }
        btnHardwareHelp?.setOnClickListener { showHardwareSetupHelp() }

        // Clear scanned data when EditText is clicked
        etScannerSink.setOnClickListener {
            clearScanResults()
        }

        // Long click for debug info
        etScannerSink.setOnLongClickListener {
            showScannerDebugInfo()
            true
        }

        // Initially disable buttons until hardware is ready
        setButtonsEnabled(false)
    }

    // === PRINTER METHODS ===

    private fun initializeEnhancedPrinter() {
        showPrinterStatus("🔍 Starting comprehensive printer initialization...")

        lifecycleScope.launch {
            try {
                // Run comprehensive debug and initialize enhanced driver
                enhancedPrinter = debugger.debugPrinterConnection(this@HardwareTestActivity)

                // Monitor connection state changes
                monitorConnectionState()
                monitorProtocolChanges()
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

    // === BARCODE SCANNER METHODS ===

    private fun setupBarcodeScanner() {
        // Initialize diagnostic utility
        diagnostic = RS232DiagnosticUtility(this)

        // Get scanner baud rate from preferences
        val scannerBaud = try {
            prefs.getScannerBaud()
        } catch (e: Exception) {
            Log.w(TAG, "Could not get scanner baud from prefs, using default 9600")
            9600
        }

        barcodeScanner = BarcodeScanner1900(
            ctx = this,
            preferredBaud = scannerBaud,
            simulateIfNoHardware = false // Enable simulation if hardware fails
        )

        // Initialize scanner
        initializeBarcodeScanner()
        configureScannerForSoftwareTrigger()
    }

    private fun configureScannerForSoftwareTrigger() {
        lifecycleScope.launch {
            try {
                showScannerStatus("🔧 Configuring scanner for software triggers...")

                // Send multiple configuration attempts
                barcodeScanner.configureSoftwareTrigger()

                delay(1000)
                showScannerStatus("✅ Scanner configuration attempted")
                showToast("📋 Scanner configured - try trigger scan now")

            } catch (e: Exception) {
                Log.e(TAG, "Scanner configuration failed", e)
                showScannerStatus("❌ Configuration failed: ${e.message}")
            }
        }
    }

    private fun initializeBarcodeScanner() {
        showScannerStatus("🔍 Initializing barcode scanner...")

        lifecycleScope.launch {
            try {
                barcodeScanner.start()

                // Monitor connection state
                monitorScannerConnection()
                monitorScannedData()
                monitorScannerStatus()

                scannerInitialized = true
                Log.i(TAG, "✅ Barcode scanner initialization completed")

                // If connection fails after timeout, suggest diagnostics
                delay(3000)
                if (barcodeScanner.connectionState.value == BarcodeScanner1900.ConnectionState.ERROR) {
                    runOnUiThread {
                        showScannerStatus("❌ Connection failed - Try running diagnostics")
                        showToast("💡 Try running RS232 diagnostics to troubleshoot")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Barcode scanner initialization failed", e)
                showScannerStatus("❌ Scanner init failed: ${e.message}")
                showToast("❌ Scanner failed - Run diagnostics to troubleshoot")
            }
        }
    }

    private fun monitorScannerConnection() {
        lifecycleScope.launch {
            barcodeScanner.connectionState.collectLatest { state ->
                val statusMessage = when (state) {
                    BarcodeScanner1900.ConnectionState.DISCONNECTED -> "⚪ Scanner disconnected"
                    BarcodeScanner1900.ConnectionState.CONNECTING -> "🔄 Scanner connecting..."
                    BarcodeScanner1900.ConnectionState.CONNECTED -> "✅ Scanner connected - Ready to scan!"
                    BarcodeScanner1900.ConnectionState.ERROR -> "❌ Scanner connection error - Check RS232"
                }

                runOnUiThread {
                    showScannerStatus(statusMessage)

                    // Enable/disable buttons based on connection state
                    val isConnected = (state == BarcodeScanner1900.ConnectionState.CONNECTED)
                    btnTriggerScan?.isEnabled = isConnected
                    btnTestConnection?.isEnabled = scannerInitialized
                    btnScannerFocus.isEnabled = true // Always enabled
                    btnClearScans?.isEnabled = true // Always enabled

                    // Update status background color
                    val backgroundColor = when (state) {
                        BarcodeScanner1900.ConnectionState.CONNECTED -> "#C8E6C9" // Light green
                        BarcodeScanner1900.ConnectionState.CONNECTING -> "#FFF3E0" // Light orange
                        BarcodeScanner1900.ConnectionState.ERROR -> "#FFCDD2" // Light red
                        BarcodeScanner1900.ConnectionState.DISCONNECTED -> "#F5F5F5" // Light gray
                    }

                    try {
                        tvScannerStatus.setBackgroundColor(android.graphics.Color.parseColor(backgroundColor))
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not set status background color")
                    }
                }

                Log.i(TAG, "Scanner connection state: $state")

                if (state == BarcodeScanner1900.ConnectionState.CONNECTED) {
                    showToast("✅ Barcode scanner ready!")

                    // Show connection details briefly
                    val connectionInfo = barcodeScanner.getConnectionInfo()
                    runOnUiThread {
                        tvConnectionInfo?.apply {
                            text = "✅ $connectionInfo"
                            visibility = android.view.View.VISIBLE
                        }
                    }

                    // Auto-hide after 5 seconds
                    lifecycleScope.launch {
                        delay(5000)
                        runOnUiThread {
                            tvConnectionInfo?.visibility = android.view.View.GONE
                        }
                    }
                } else if (state == BarcodeScanner1900.ConnectionState.ERROR) {
                    showToast("❌ Scanner connection error!")
                }
            }
        }
    }

    private fun monitorScannedData() {
        lifecycleScope.launch {
            barcodeScanner.scans.collectLatest { scannedCode ->
                Log.i(TAG, "📸 Received scan: '$scannedCode'")

                runOnUiThread {
                    // Format the scanned data nicely
                    val currentText = etScannerSink.text.toString()
                    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        .format(Date())

                    // Analyze barcode type
                    val barcodeType = detectBarcodeType(scannedCode)

                    val formattedScan = "[$timestamp] $barcodeType: $scannedCode"

                    val newText = if (currentText.isEmpty()) {
                        formattedScan
                    } else {
                        "$currentText\n$formattedScan"
                    }

                    etScannerSink.setText(newText)

                    // Scroll to bottom
                    etScannerSink.setSelection(etScannerSink.text.length)

                    // Show toast notification
                    showToast("📸 $barcodeType: $scannedCode")

                    // Update status
                    showScannerStatus("✅ Last scan: $barcodeType")

                    // Log for audit
                    AuditLogger.log("INFO", "BARCODE_SCAN_SUCCESS", "type=$barcodeType;code=$scannedCode")
                }
            }
        }
    }

    private fun monitorScannerStatus() {
        lifecycleScope.launch {
            barcodeScanner.statusMessages.collectLatest { message ->
                Log.d(TAG, "Scanner status: $message")
                runOnUiThread {
                    showScannerStatus(message)
                }
            }
        }
    }

    private fun triggerScan() {
        if (!scannerInitialized) {
            showToast("⚠️ Scanner not initialized yet")
            return
        }

        showScannerStatus("📸 Triggering scan...")

        try {
            barcodeScanner.activateSerialTrigger()

            // Auto stop after 4s if no decode
            lifecycleScope.launch {
                delay(4000)
                barcodeScanner.deactivateSerialTrigger()
            }

            AuditLogger.log("INFO", "BARCODE_SCAN_TRIGGERED", "user_action=button_press")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger scan", e)
            showScannerStatus("❌ Failed to trigger scan")
            showToast("❌ Scan trigger failed!")
        }
    }

    private fun focusScannerInput() {
        etScannerSink.requestFocus()
        showScannerStatus("📱 Input area focused - Ready for scans")
        Log.d(TAG, "Scanner input area focused")
    }

    private fun clearScanResults() {
        etScannerSink.setText("")
        showScannerStatus("🗑️ Scan results cleared")
        showToast("🗑️ Results cleared")
        Log.d(TAG, "Scan results cleared by user")
    }

    private fun testScannerConnection() {
        if (!scannerInitialized) {
            showToast("⚠️ Scanner not initialized")
            return
        }

        lifecycleScope.launch {
            try {
                showScannerStatus("🔍 Testing scanner connection...")
                showToast("🔍 Testing connection...")

                val connectionInfo = barcodeScanner.getConnectionInfo()
                showScannerStatus("🔍 Connection test completed")

                tvConnectionInfo?.apply {
                    text = connectionInfo
                    visibility = android.view.View.VISIBLE
                }

                Log.i(TAG, "Scanner connection test: $connectionInfo")
                showToast("✅ Connection test completed")

                // Hide connection info after 10 seconds
                lifecycleScope.launch {
                    delay(10000)
                    tvConnectionInfo?.visibility = android.view.View.GONE
                }

            } catch (e: Exception) {
                showScannerStatus("❌ Connection test failed: ${e.message}")
                showToast("❌ Connection test failed!")
                Log.e(TAG, "Connection test failed", e)
            }
        }
    }

    private fun runRS232Diagnostics() {
        showToast("🔍 Running RS232 diagnostics...")
        showScannerStatus("🔍 Running comprehensive diagnostics...")

        lifecycleScope.launch {
            try {
                val result = diagnostic.runDiagnostics()
                val report = diagnostic.generateReport(result)

                runOnUiThread {
                    etScannerSink.setText(report)
                    showScannerStatus("✅ Diagnostics complete - check results above")
                    showToast("✅ Diagnostics complete!")
                }

                Log.i(TAG, "=== FULL DIAGNOSTIC REPORT ===")
                Log.i(TAG, report)
                Log.i(TAG, "===============================")

                val summary = generateDiagnosticSummary(result)
                runOnUiThread {
                    tvConnectionInfo?.apply {
                        text = summary
                        visibility = android.view.View.VISIBLE
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Diagnostic failed", e)
                runOnUiThread {
                    showScannerStatus("❌ Diagnostic failed: ${e.message}")
                    showToast("❌ Diagnostic failed!")
                }
            }
        }
    }

    private fun showHardwareSetupHelp() {
        val helpText = """
📋 HARDWARE SETUP GUIDE

Your Configuration:
• Honeywell Xenon 1900 (15-pin SUB-D)
• SUB-D to RS232 converter cable  
• Direct RS232 connection to Android box

Required Connections:
• Pin 3 (Scanner TX) → Pin 2 (Android RX)
• Pin 4 (Scanner RX) → Pin 3 (Android TX)
• Ground connections

Troubleshooting Steps:
1. Run RS232 Diagnostics
2. Check cable connections
3. Verify power to scanner
4. Test with different baud rates

If direct RS232 fails:
• Use USB-to-RS232 adapter instead
• Try simulation mode for testing UI
• Consider Arduino/microcontroller bridge

Press 'Run Diagnostics' for detailed analysis.
        """.trimIndent()

        etScannerSink.setText(helpText)
        showScannerStatus("📋 Hardware setup help displayed")
    }

    private fun showScannerDebugInfo() {
        if (!scannerInitialized) {
            showToast("⚠️ Scanner not initialized")
            return
        }

        lifecycleScope.launch {
            try {
                val debugInfo = buildString {
                    appendLine("🔍 BARCODE SCANNER DEBUG INFO")
                    appendLine("============================")
                    appendLine("Connection: ${barcodeScanner.connectionState.value}")
                    appendLine("Info: ${barcodeScanner.getConnectionInfo()}")
                    appendLine("Initialized: $scannerInitialized")
                    appendLine("Current Time: ${Date()}")
                    appendLine("============================")
                }

                Log.i(TAG, debugInfo)

                val currentText = etScannerSink.text.toString()
                etScannerSink.setText("$debugInfo\n$currentText")

                showToast("🔍 Debug info added to results")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to show debug info", e)
                showToast("❌ Debug info failed")
            }
        }
    }

    private fun generateDiagnosticSummary(result: RS232DiagnosticUtility.DiagnosticResult): String {
        val accessiblePorts = result.portTests.count { it.canOpen && it.canRead && it.canWrite }

        return buildString {
            appendLine("📊 DIAGNOSTIC SUMMARY")
            appendLine("Device: ${result.deviceInfo.manufacturer} ${result.deviceInfo.model}")
            appendLine("Android: ${result.deviceInfo.androidVersion}")
            appendLine("Root: ${if (result.deviceInfo.isRooted) "✅ Yes" else "❌ No"}")
            appendLine("Accessible Ports: $accessiblePorts/${result.availablePorts.size}")

            if (accessiblePorts > 0) {
                appendLine("✅ RS232 connection possible")
                result.portTests.filter { it.canOpen && it.canRead && it.canWrite }
                    .take(2).forEach {
                        appendLine("  Recommended: ${it.port.path}")
                    }
            } else {
                appendLine("❌ No accessible RS232 ports")
                if (!result.deviceInfo.isRooted) {
                    appendLine("💡 Root access may be required")
                }
            }
        }
    }

    // Simple barcode type detection
    private fun detectBarcodeType(code: String): String {
        return when {
            code.length == 13 && code.all { it.isDigit() } -> "EAN-13"
            code.length == 8 && code.all { it.isDigit() } -> "EAN-8"
            code.length == 12 && code.all { it.isDigit() } -> "UPC-A"
            code.all { it.isDigit() || it.isUpperCase() || it == ' ' || it == '-' || it == '.' } -> "Code 39"
            code.length >= 6 -> "Code 128"
            else -> "Unknown"
        }
    }

    private fun showScannerStatus(message: String) {
        runOnUiThread {
            tvScannerStatus.text = message
            Log.d(TAG, "Scanner Status: $message")
        }
    }

    // === PRINTER TEST METHODS ===

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
                showPrinterStatus("📊 Starting barcode printing test...")

                // Use the new comprehensive barcode test
                val result = enhancedPrinter.printBarcodeTest()

                if (result.isSuccess) {
                    showPrinterStatus("✅ All barcode tests completed successfully!")
                    showToast("✅ All barcodes printed successfully!")
                    AuditLogger.log("SUCCESS", "BARCODE_TEST_SUCCESS", "All barcode types printed")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    showPrinterStatus("❌ Barcode test failed: $error")
                    showToast("❌ Some barcodes failed to print!")
                    AuditLogger.log("ERROR", "BARCODE_TEST_FAIL", "msg=$error")
                }

            } catch (e: Exception) {
                showPrinterStatus("❌ Barcode test error: ${e.message}")
                showToast("❌ Barcode test error!")
                AuditLogger.log("ERROR", "BARCODE_TEST_EXCEPTION", "msg=${e.message}")
            }
        }
    }

    private fun testSingleBarcode(barcodeType: String, data: String) {
        if (!printerInitialized) {
            showToast("⚠️ Printer not initialized yet")
            return
        }

        lifecycleScope.launch {
            try {
                showPrinterStatus("📊 Printing $barcodeType...")

                val result = when (barcodeType.uppercase()) {
                    "CODE128" -> enhancedPrinter.printCode128(data)
                    "EAN13" -> enhancedPrinter.printEAN13(data)
                    "QRCODE" -> enhancedPrinter.printQRCode(data)
                    "CODE39" -> enhancedPrinter.printCode39(data)
                    else -> {
                        // Use custom barcode config for other types
                        val config = CustomTG2480HIIIDriver.BarcodeConfig(
                            type = when (barcodeType.uppercase()) {
                                "UPC-A" -> CustomTG2480HIIIDriver.BarcodeType.UPC_A
                                "UPC-E" -> CustomTG2480HIIIDriver.BarcodeType.UPC_E
                                "EAN8" -> CustomTG2480HIIIDriver.BarcodeType.EAN8
                                "ITF" -> CustomTG2480HIIIDriver.BarcodeType.ITF
                                "CODABAR" -> CustomTG2480HIIIDriver.BarcodeType.CODABAR
                                "CODE93" -> CustomTG2480HIIIDriver.BarcodeType.CODE93
                                "CODE32" -> CustomTG2480HIIIDriver.BarcodeType.CODE32
                                else -> CustomTG2480HIIIDriver.BarcodeType.CODE128 // Default fallback
                            },
                            data = data,
                            height = 162,
                            centered = true
                        )
                        enhancedPrinter.printBarcode(config)
                    }
                }

                if (result.isSuccess) {
                    showPrinterStatus("✅ $barcodeType printed successfully!")
                    showToast("✅ $barcodeType completed!")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    showPrinterStatus("❌ $barcodeType failed: $error")
                    showToast("❌ $barcodeType failed!")
                }

            } catch (e: Exception) {
                showPrinterStatus("❌ $barcodeType error: ${e.message}")
                showToast("❌ $barcodeType error!")
            }
        }
    }

    private fun testReceiptWithBarcode() {
        if (!printerInitialized) {
            showToast("⚠️ Printer not initialized yet")
            return
        }

        lifecycleScope.launch {
            try {
                showPrinterStatus("🧾 Printing receipt with barcode...")

                // Print receipt header
                enhancedPrinter.printText(
                    text = buildString {
                        appendLine("PUDO KIOSK RECEIPT")
                        appendLine("==================")
                        appendLine("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                        appendLine("Transaction ID: TXN001234")
                        appendLine("==================")
                        appendLine()
                    },
                    fontSize = 1,
                    bold = true,
                    centered = true
                )

                // Print tracking barcode
                val barcodeResult = enhancedPrinter.printCode128("TXN001234567890")

                if (barcodeResult.isSuccess) {
                    // Print footer
                    enhancedPrinter.printText(
                        text = buildString {
                            appendLine()
                            appendLine("Thank you!")
                            appendLine("==================")
                            appendLine()
                        },
                        centered = true
                    )

                    showPrinterStatus("✅ Receipt with barcode printed!")
                    showToast("✅ Receipt completed!")
                } else {
                    showPrinterStatus("❌ Receipt barcode failed")
                    showToast("❌ Receipt barcode failed!")
                }

            } catch (e: Exception) {
                showPrinterStatus("❌ Receipt printing error: ${e.message}")
                showToast("❌ Receipt error!")
            }
        }
    }

    private fun testShippingLabel() {
        if (!printerInitialized) {
            showToast("⚠️ Printer not initialized yet")
            return
        }

        lifecycleScope.launch {
            try {
                showPrinterStatus("🏷️ Printing shipping label...")

                // Label header
                enhancedPrinter.printText(
                    text = "PUDO SHIPPING LABEL\n" + "=".repeat(30) + "\n",
                    fontSize = 2,
                    bold = true,
                    centered = true
                )

                // Recipient info
                enhancedPrinter.printText(
                    text = buildString {
                        appendLine("TO: John Doe")
                        appendLine("123 Main Street")
                        appendLine("Harare, Zimbabwe")
                        appendLine()
                    },
                    fontSize = 1
                )

                // Tracking number as Code 128
                enhancedPrinter.printText("TRACKING NUMBER:", bold = true, centered = true)
                val trackingResult = enhancedPrinter.printCode128("1Z999AA1234567890")

                if (trackingResult.isSuccess) {
                    // QR code with tracking info
                    enhancedPrinter.printText("\nSCAN FOR DETAILS:", bold = true, centered = true)
                    val qrResult = enhancedPrinter.printQRCode("https://pudo.co.zw/track/1Z999AA1234567890")

                    if (qrResult.isSuccess) {
                        enhancedPrinter.printText(
                            text = "\n" + "=".repeat(30) + "\n",
                            centered = true
                        )
                        showPrinterStatus("✅ Shipping label printed!")
                        showToast("✅ Shipping label completed!")
                    } else {
                        showPrinterStatus("❌ QR code failed")
                        showToast("❌ QR code failed!")
                    }
                } else {
                    showPrinterStatus("❌ Tracking barcode failed")
                    showToast("❌ Tracking barcode failed!")
                }

            } catch (e: Exception) {
                showPrinterStatus("❌ Shipping label error: ${e.message}")
                showToast("❌ Shipping label error!")
            }
        }
    }

    private fun runComprehensiveDebug() {
        lifecycleScope.launch {
            try {
                showPrinterStatus("🔍 Running comprehensive debug...")
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
            } catch (e: Exception) {
                showToast("❌ Protocol test failed: ${e.message}")
            }
        }
    }

    // === UTILITY METHODS ===

    private fun setButtonsEnabled(enabled: Boolean) {
        runOnUiThread {
            // Printer buttons
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

    private fun showPrinterStatus(message: String) {
        runOnUiThread {
            tvPrinterStatus.text = message
            Log.d(TAG, "Printer Status: $message")
        }
    }

    private fun updatePrinterDetails(status: CustomTG2480HIIIDriver.CustomPrinterStatus) {
        val details = buildString {
            append("Ready: ${if (status.isReady) "✔" else "✗"} | ")
            append("Paper: ${if (!status.noPaper) "✔" else "✗"} | ")
            append("Rolling: ${if (status.paperRolling) "↻" else "○"} | ")
            append("LF: ${if (status.lfPressed) "✔" else "✗"} | ")
            append("Protocol: ${status.protocol.name}")

            if (!status.isOperational) append(" | ⚠️ CHECK REQUIRED")
        }

        runOnUiThread {
            tvPrinterDetails.text = details
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this@HardwareTestActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private operator fun String.times(count: Int): String {
        return this.repeat(count)
    }

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

        try {
            if (scannerInitialized) {
                barcodeScanner.stop()
                Log.i(TAG, "Barcode scanner stopped")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping barcode scanner: ${e.message}")
        }

        AuditLogger.log("INFO", "HARDWARE_TEST_ACTIVITY_DESTROYED", "All hardware cleaned up")
    }

    /**
     * Enhanced Printer Connection Debugger Class
     */
    inner class PrinterConnectionDebugger {

        private val debugTag = "PrinterDebugger"

        suspend fun debugPrinterConnection(context: Context): CustomTG2480HIIIDriver {
            Log.i(debugTag, "🔍 ========== COMPREHENSIVE PRINTER DEBUG SESSION ==========")

            debugUSBManifestConfiguration(context)
            debugAllUSBDevices(context)
            debugCustomAPIAvailability()
            debugSerialPortAvailability(context)

            Log.i(debugTag, "🚀 Initializing Enhanced TG2480HIII Driver...")
            val enhancedDriver = CustomTG2480HIIIDriver(
                context = context,
                simulate = false,
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

                    if (device.vendorId == 3540) {
                        Log.i(debugTag, "  🎯 THIS IS THE CUSTOM TG2480HIII PRINTER!")
                        Log.i(debugTag, "  🔋 Printer Details:")
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