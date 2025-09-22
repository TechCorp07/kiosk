package com.blitztech.pudokiosk.ui

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

// Hardware component imports
import com.blitztech.pudokiosk.deviceio.rs485.LockerController
import com.blitztech.pudokiosk.deviceio.rs485.LockerConfiguration
import com.blitztech.pudokiosk.deviceio.rs232.BarcodeScanner // Object, not class
import com.blitztech.pudokiosk.deviceio.printer.CustomTG2480HIIIDriver

/**
 * Unified Hardware Test Activity for PUDO Kiosk
 * Tests the three actual hardware components:
 * 1. STM32L412 Locker Controller (RS485)
 * 2. Honeywell Xenon 1900 Barcode Scanner (RS232)
 * 3. Custom TG2480HIII Thermal Printer (USB/Custom API)
 */
class HardwareTestActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HardwareTest"
    }

    // === LOCKER CONTROLLER COMPONENTS ===
    private lateinit var lockerController: LockerController
    private lateinit var etLockerId: EditText
    private lateinit var btnOpenLocker: Button
    private lateinit var btnCheckLocker: Button
    private lateinit var btnTestStation: Button
    private lateinit var btnSystemDiagnostics: Button
    private lateinit var swSimulateLockers: Switch
    private lateinit var tvLockerStatus: TextView
    private lateinit var tvSystemStatus: TextView
    private lateinit var spStation: Spinner
    private lateinit var spLockNumber: Spinner

    // === BARCODE SCANNER COMPONENTS ===
    private lateinit var btnScannerFocus: Button
    private lateinit var etScannerResults: EditText
    private lateinit var btnTriggerScan: Button
    private lateinit var btnClearScans: Button
    private lateinit var btnScannerReconnect: Button
    private lateinit var tvScannerStatus: TextView
    private lateinit var tvScannerInfo: TextView

    // === THERMAL PRINTER COMPONENTS ===
    private lateinit var printerDriver: CustomTG2480HIIIDriver
    private lateinit var btnPrintTest: Button
    private lateinit var btnPrintReceipt: Button
    private lateinit var btnPrintBarcode: Button
    private lateinit var btnPrinterStatus: Button
    private lateinit var tvPrinterStatus: TextView
    private lateinit var tvPrinterDetails: TextView

    // === GENERAL COMPONENTS ===
    private lateinit var progressBar: ProgressBar
    private lateinit var btnRunAllTests: Button
    private lateinit var btnResetAll: Button

    // === STATE TRACKING ===
    private var lockerInitialized = false
    private var scannerInitialized = false
    private var printerInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hardware_test)

        Log.i(TAG, "=== PUDO Kiosk Hardware Test Center Started ===")

        initializeViews()
        setupEventListeners()
        initializeHardware()
    }

    private fun initializeViews() {
        // Locker Controller UI
        etLockerId = findViewById(R.id.etLockerId)
        btnOpenLocker = findViewById(R.id.btnOpenLocker)
        btnCheckLocker = findViewById(R.id.btnCheckLocker)
        btnTestStation = findViewById(R.id.btnTestStation)
        btnSystemDiagnostics = findViewById(R.id.btnSystemDiagnostics)
        swSimulateLockers = findViewById(R.id.swSimulateLockers)
        tvLockerStatus = findViewById(R.id.tvLockerStatus)
        tvSystemStatus = findViewById(R.id.tvSystemStatus)
        spStation = findViewById(R.id.spStation)
        spLockNumber = findViewById(R.id.spLockNumber)

        // Barcode Scanner UI
        btnScannerFocus = findViewById(R.id.btnScannerFocus)
        etScannerResults = findViewById(R.id.etScannerResults)
        btnTriggerScan = findViewById(R.id.btnTriggerScan)
        btnClearScans = findViewById(R.id.btnClearScans)
        btnScannerReconnect = findViewById(R.id.btnScannerReconnect)
        tvScannerStatus = findViewById(R.id.tvScannerStatus)
        tvScannerInfo = findViewById(R.id.tvScannerInfo)

        // Thermal Printer UI
        btnPrintTest = findViewById(R.id.btnPrintTest)
        btnPrintReceipt = findViewById(R.id.btnPrintReceipt)
        btnPrintBarcode = findViewById(R.id.btnPrintBarcode)
        btnPrinterStatus = findViewById(R.id.btnPrinterStatus)
        tvPrinterStatus = findViewById(R.id.tvPrinterStatus)
        tvPrinterDetails = findViewById(R.id.tvPrinterDetails)

        // General UI
        progressBar = findViewById(R.id.progressBar)
        btnRunAllTests = findViewById(R.id.btnRunAllTests)
        btnResetAll = findViewById(R.id.btnResetAll)

        setupSpinners()
        setButtonsEnabled(false)
    }

    private fun setupSpinners() {
        // Station spinner (0-3)
        val stationAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            listOf("Station 0 (DIP: 00)", "Station 1 (DIP: 01)", "Station 2 (DIP: 10)", "Station 3 (DIP: 11)"))
        stationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spStation.adapter = stationAdapter

        // Lock number spinner (1-16)
        val lockAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            (1..16).map { "Lock $it" })
        lockAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spLockNumber.adapter = lockAdapter
    }

    private fun setupEventListeners() {
        // === LOCKER CONTROLLER EVENTS ===
        swSimulateLockers.setOnCheckedChangeListener { _, isChecked ->
            reinitializeLockerController(isChecked)
        }

        btnOpenLocker.setOnClickListener {
            val lockerId = etLockerId.text.toString().trim()
            if (lockerId.isNotEmpty()) {
                openLocker(lockerId)
            } else {
                updateLockerStatus("Please enter a locker ID (e.g., M1, M25)")
            }
        }

        btnCheckLocker.setOnClickListener {
            val lockerId = etLockerId.text.toString().trim()
            if (lockerId.isNotEmpty()) {
                checkLockerStatus(lockerId)
            } else {
                updateLockerStatus("Please enter a locker ID")
            }
        }

        btnTestStation.setOnClickListener {
            val station = spStation.selectedItemPosition
            testStation(station)
        }

        btnSystemDiagnostics.setOnClickListener {
            runSystemDiagnostics()
        }

        // === BARCODE SCANNER EVENTS ===
        btnScannerFocus.setOnClickListener {
            focusScannerInput()
        }

        btnTriggerScan.setOnClickListener {
            triggerBarcodeScan()
        }

        btnClearScans.setOnClickListener {
            clearScanResults()
        }

        btnScannerReconnect.setOnClickListener {
            reconnectScanner()
        }

        etScannerResults.setOnClickListener {
            clearScanResults()
        }

        // === THERMAL PRINTER EVENTS ===
        btnPrintTest.setOnClickListener {
            testBasicPrinting()
        }

        btnPrintReceipt.setOnClickListener {
            printSampleReceipt()
        }

        btnPrintBarcode.setOnClickListener {
            printBarcodeTest()
        }

        btnPrinterStatus.setOnClickListener {
            checkPrinterStatus()
        }

        // === GENERAL EVENTS ===
        btnRunAllTests.setOnClickListener {
            runComprehensiveTests()
        }

        btnResetAll.setOnClickListener {
            resetAllHardware()
        }
    }

    private fun initializeHardware() {
        showProgress(true)
        updateStatus("Initializing hardware components...")

        lifecycleScope.launch {
            try {
                // Initialize all three hardware components
                initializeLockerController()
                initializeBarcodeScanner()
                initializeThermalPrinter()

                // Enable UI after initialization
                setButtonsEnabled(true)
                updateStatus("All hardware initialized successfully!")

            } catch (e: Exception) {
                Log.e(TAG, "Hardware initialization failed", e)
                updateStatus("Hardware initialization failed: ${e.message}")
            } finally {
                showProgress(false)
            }
        }
    }

    // === LOCKER CONTROLLER METHODS ===

    private fun initializeLockerController() {
        val simulate = swSimulateLockers.isChecked
        lockerController = LockerController(this, simulate = simulate)
        lockerInitialized = true

        updateLockerStatus("Locker controller initialized (${if (simulate) "simulation" else "hardware"} mode)")
        Log.i(TAG, "Locker controller initialized in ${if (simulate) "simulation" else "hardware"} mode")
    }

    private fun reinitializeLockerController(simulate: Boolean) {
        lifecycleScope.launch {
            try {
                if (lockerInitialized) {
                    lockerController.close()
                }

                lockerController = LockerController(this@HardwareTestActivity, simulate = simulate)
                lockerInitialized = true

                updateLockerStatus("Switched to ${if (simulate) "simulation" else "hardware"} mode")
                Log.i(TAG, "Locker controller reinitialized in ${if (simulate) "simulation" else "hardware"} mode")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to reinitialize locker controller", e)
                updateLockerStatus("Failed to switch mode: ${e.message}")
            }
        }
    }

    private fun openLocker(lockerId: String) {
        showProgress(true)
        updateLockerStatus("Opening locker $lockerId...")

        lifecycleScope.launch {
            try {
                val success = lockerController.openLocker(lockerId)
                val mapping = lockerController.getLockerMapping(lockerId)

                if (success) {
                    updateLockerStatus("✅ Successfully opened $lockerId\n$mapping")
                    showToast("Locker $lockerId opened successfully!")
                } else {
                    updateLockerStatus("❌ Failed to open $lockerId\n$mapping")
                    showToast("Failed to open locker $lockerId")
                }
            } catch (e: Exception) {
                updateLockerStatus("❌ Error opening $lockerId: ${e.message}")
                Log.e(TAG, "Error opening locker", e)
            } finally {
                showProgress(false)
            }
        }
    }

    private fun checkLockerStatus(lockerId: String) {
        showProgress(true)
        updateLockerStatus("Checking status of locker $lockerId...")

        lifecycleScope.launch {
            try {
                val isClosed = lockerController.isClosed(lockerId)
                val mapping = lockerController.getLockerMapping(lockerId)
                val status = if (isClosed) "CLOSED" else "OPEN"

                updateLockerStatus("📋 Locker $lockerId is $status\n$mapping")
                showToast("Locker $lockerId is $status")
            } catch (e: Exception) {
                updateLockerStatus("❌ Error checking $lockerId status: ${e.message}")
                Log.e(TAG, "Error checking locker status", e)
            } finally {
                showProgress(false)
            }
        }
    }

    private fun testStation(station: Int) {
        showProgress(true)
        updateLockerStatus("Testing station $station...")

        lifecycleScope.launch {
            try {
                val success = lockerController.testStation(station)
                val dipSetting = when (station) {
                    0 -> "00"
                    1 -> "01"
                    2 -> "10"
                    3 -> "11"
                    else -> "??"
                }

                if (success) {
                    updateLockerStatus("✅ Station $station (DIP: $dipSetting) is ONLINE")
                } else {
                    updateLockerStatus("❌ Station $station (DIP: $dipSetting) is OFFLINE")
                }
            } catch (e: Exception) {
                updateLockerStatus("❌ Error testing station $station: ${e.message}")
                Log.e(TAG, "Error testing station", e)
            } finally {
                showProgress(false)
            }
        }
    }

    private fun runSystemDiagnostics() {
        showProgress(true)
        updateLockerStatus("Running system diagnostics...")

        lifecycleScope.launch {
            try {
                val systemStatus = lockerController.getSystemStatus()

                val statusBuilder = StringBuilder().apply {
                    appendLine("🔧 LOCKER SYSTEM DIAGNOSTICS")
                    appendLine("=" * 30)
                    appendLine("Total Stations: 4")
                    appendLine("Expected Capacity: 64 lockers")
                    appendLine()

                    systemStatus.forEach { (station, isOnline) ->
                        val dipSetting = when (station) {
                            0 -> "00"
                            1 -> "01"
                            2 -> "10"
                            3 -> "11"
                            else -> "??"
                        }
                        val status = if (isOnline) "✅ ONLINE" else "❌ OFFLINE"
                        appendLine("Station $station (DIP: $dipSetting): $status")

                        if (isOnline) {
                            val lockerRange = "${station * 16 + 1}-${(station + 1) * 16}"
                            appendLine("  → Controls lockers M$lockerRange")
                        }
                    }

                    appendLine()
                    val onlineCount = systemStatus.values.count { it }
                    appendLine("Summary: $onlineCount/4 stations online")

                    if (onlineCount == 4) {
                        appendLine("🟢 All systems operational!")
                    } else {
                        appendLine("🟡 Some stations offline - check connections")
                    }

                    appendLine()
                    appendLine("Configuration:")
                    appendLine("• Protocol: Winnsen Custom over RS485")
                    appendLine("• Baud Rate: 9600")
                    appendLine("• Data Format: 8N1")
                    appendLine("• Locks per Board: 16")
                    appendLine("• Simulation Mode: ${swSimulateLockers.isChecked}")
                }

                tvSystemStatus.text = statusBuilder.toString()
                updateLockerStatus("System diagnostics completed")

            } catch (e: Exception) {
                updateLockerStatus("❌ Error running diagnostics: ${e.message}")
                Log.e(TAG, "Error running diagnostics", e)
            } finally {
                showProgress(false)
            }
        }
    }

    // === BARCODE SCANNER METHODS ===

    private fun initializeBarcodeScanner() {
        updateScannerStatus("🔍 Initializing Honeywell Xenon 1900...")

        try {
            // Initialize the BarcodeScanner object
            BarcodeScanner.initialize(this)

            // Monitor scanner state and data
            monitorScannerConnection()
            monitorScannedData()

            scannerInitialized = true
            Log.i(TAG, "Barcode scanner initialization started")

        } catch (e: Exception) {
            Log.e(TAG, "Barcode scanner initialization failed", e)
            updateScannerStatus("❌ Scanner init failed: ${e.message}")
        }
    }

    private fun monitorScannerConnection() {
        lifecycleScope.launch {
            BarcodeScanner.connectionState.collectLatest { state ->
                val statusMessage = when (state) {
                    BarcodeScanner.ConnectionState.DISCONNECTED -> "⚪ Scanner disconnected"
                    BarcodeScanner.ConnectionState.CONNECTING -> "🔄 Scanner connecting..."
                    BarcodeScanner.ConnectionState.CONNECTED -> "✅ Scanner connected - Ready to scan!"
                    BarcodeScanner.ConnectionState.PERMISSION_DENIED -> "❌ USB permission denied"
                    BarcodeScanner.ConnectionState.ERROR -> "❌ Scanner connection error"
                }

                runOnUiThread {
                    updateScannerStatus(statusMessage)

                    val isConnected = (state == BarcodeScanner.ConnectionState.CONNECTED)
                    btnTriggerScan.isEnabled = isConnected

                    if (isConnected) {
                        val connectionInfo = BarcodeScanner.getConnectionInfo()
                        tvScannerInfo.text = connectionInfo
                        showToast("Barcode scanner ready!")
                    }
                }

                Log.i(TAG, "Scanner connection state: $state")
            }
        }
    }

    private fun monitorScannedData() {
        lifecycleScope.launch {
            BarcodeScanner.scannedData.collectLatest { scannedCode ->
                Log.i(TAG, "📸 Received scan: '$scannedCode'")

                runOnUiThread {
                    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    val barcodeType = detectBarcodeType(scannedCode)
                    val formattedScan = "[$timestamp] $barcodeType: $scannedCode"

                    val currentText = etScannerResults.text.toString()
                    val newText = if (currentText.isEmpty()) {
                        formattedScan
                    } else {
                        "$currentText\n$formattedScan"
                    }

                    etScannerResults.setText(newText)
                    etScannerResults.setSelection(etScannerResults.text.length)

                    updateScannerStatus("✅ Last scan: $barcodeType")
                    showToast("📸 $barcodeType: $scannedCode")
                }
            }
        }
    }

    private fun triggerBarcodeScan() {
        if (!scannerInitialized) {
            showToast("Scanner not initialized yet")
            return
        }

        try {
            updateScannerStatus("📸 Triggering scan...")
            // Note: The BarcodeScanner object handles triggering automatically
            // when it receives data from the hardware
            showToast("Scanner activated - scan a barcode now")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger scan", e)
            updateScannerStatus("❌ Failed to trigger scan")
        }
    }

    private fun focusScannerInput() {
        etScannerResults.requestFocus()
        updateScannerStatus("📱 Input area focused - Ready for scans")
    }

    private fun clearScanResults() {
        etScannerResults.setText("")
        updateScannerStatus("🗑️ Scan results cleared")
        showToast("Results cleared")
    }

    private fun reconnectScanner() {
        try {
            updateScannerStatus("🔄 Reconnecting scanner...")
            BarcodeScanner.reconnect()
            showToast("Scanner reconnection initiated")
        } catch (e: Exception) {
            updateScannerStatus("❌ Reconnection failed: ${e.message}")
        }
    }

    // === THERMAL PRINTER METHODS ===

    private suspend fun initializeThermalPrinter() {
        updatePrinterStatus("🖨️ Initializing Custom TG2480HIII...")

        try {
            printerDriver = CustomTG2480HIIIDriver(
                context = this,
                enableAutoReconnect = true
            )

            val result = printerDriver.initialize()

            if (result.isSuccess) {
                printerInitialized = true
                updatePrinterStatus("✅ Printer initialized successfully")
                monitorPrinterStatus()
                Log.i(TAG, "Thermal printer initialized successfully")
            } else {
                updatePrinterStatus("❌ Printer initialization failed: ${result.exceptionOrNull()?.message}")
                Log.e(TAG, "Printer initialization failed", result.exceptionOrNull())
            }

        } catch (e: Exception) {
            Log.e(TAG, "Thermal printer initialization failed", e)
            updatePrinterStatus("❌ Printer init failed: ${e.message}")
        }
    }

    private fun monitorPrinterStatus() {
        lifecycleScope.launch {
            printerDriver.connectionState.collectLatest { state ->
                val statusMessage = when (state) {
                    CustomTG2480HIIIDriver.ConnectionState.DISCONNECTED -> "⚪ Printer disconnected"
                    CustomTG2480HIIIDriver.ConnectionState.CONNECTING -> "🔄 Printer connecting..."
                    CustomTG2480HIIIDriver.ConnectionState.CONNECTED -> "✅ Printer connected and ready"
                    CustomTG2480HIIIDriver.ConnectionState.ERROR -> "❌ Printer connection error"
                    CustomTG2480HIIIDriver.ConnectionState.PERMISSION_DENIED -> "❌ USB permission denied"
                    CustomTG2480HIIIDriver.ConnectionState.DEVICE_NOT_FOUND -> "❌ Printer device not found"
                }

                runOnUiThread {
                    updatePrinterStatus(statusMessage)

                    val isConnected = (state == CustomTG2480HIIIDriver.ConnectionState.CONNECTED)
                    btnPrintTest.isEnabled = isConnected
                    btnPrintReceipt.isEnabled = isConnected
                    btnPrintBarcode.isEnabled = isConnected
                }

                Log.i(TAG, "Printer connection state: $state")
            }
        }
    }

    private fun testBasicPrinting() {
        if (!printerInitialized) {
            showToast("Printer not initialized yet")
            return
        }

        lifecycleScope.launch {
            try {
                updatePrinterStatus("🖨️ Testing basic printing...")

                val testText = buildString {
                    appendLine("=== PUDO KIOSK TEST ===")
                    appendLine()
                    appendLine("Date: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}")
                    appendLine("Time: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}")
                    appendLine()
                    appendLine("Hardware Test Status:")
                    appendLine("Locker: ${if (lockerInitialized) "OK" else "FAIL"}")
                    appendLine("Scanner: ${if (scannerInitialized) "OK" else "FAIL"}")
                    appendLine("Printer: ${if (printerInitialized) "OK" else "FAIL"}")
                    appendLine()
                    appendLine("=====================")
                    appendLine()
                }

                val result = printerDriver.printText(testText, fontSize = 1, centered = false)

                if (result.isSuccess) {
                    updatePrinterStatus("✅ Print test successful")
                    showToast("Print test completed!")
                } else {
                    updatePrinterStatus("❌ Print test failed: ${result.exceptionOrNull()?.message}")
                    showToast("Print test failed!")
                }

            } catch (e: Exception) {
                updatePrinterStatus("❌ Print test error: ${e.message}")
                Log.e(TAG, "Print test failed", e)
            }
        }
    }

    private fun printSampleReceipt() {
        if (!printerInitialized) {
            showToast("Printer not initialized yet")
            return
        }

        lifecycleScope.launch {
            try {
                updatePrinterStatus("🧾 Printing sample receipt...")

                val receiptText = buildString {
                    appendLine("    PUDO KIOSK RECEIPT")
                    appendLine("    Hardware Test Receipt")
                    appendLine("=" * 32)
                    appendLine()
                    appendLine("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                    appendLine("Transaction: TXN-${System.currentTimeMillis()}")
                    appendLine()
                    appendLine("Hardware Status:")
                    appendLine("  Locker Controller    ${if (lockerInitialized) "OK" else "FAIL"}")
                    appendLine("  Barcode Scanner      ${if (scannerInitialized) "OK" else "FAIL"}")
                    appendLine("  Thermal Printer      ${if (printerInitialized) "OK" else "FAIL"}")
                    appendLine()
                    appendLine("Test Mode: ${if (swSimulateLockers.isChecked) "SIMULATION" else "HARDWARE"}")
                    appendLine()
                    appendLine("Thank you for testing!")
                    appendLine("PUDO Kiosk System v1.0")
                    appendLine("=" * 32)
                    appendLine()
                }

                val result = printerDriver.printText(receiptText, fontSize = 1, centered = false)

                if (result.isSuccess) {
                    updatePrinterStatus("✅ Receipt printed successfully")
                    showToast("Receipt printed!")
                } else {
                    updatePrinterStatus("❌ Receipt printing failed: ${result.exceptionOrNull()?.message}")
                    showToast("Receipt printing failed!")
                }

            } catch (e: Exception) {
                updatePrinterStatus("❌ Receipt printing error: ${e.message}")
                Log.e(TAG, "Receipt printing failed", e)
            }
        }
    }

    private fun printBarcodeTest() {
        if (!printerInitialized) {
            showToast("Printer not initialized yet")
            return
        }

        lifecycleScope.launch {
            try {
                updatePrinterStatus("📊 Testing barcode printing...")

                val result = printerDriver.printBarcodeTest()

                if (result.isSuccess) {
                    updatePrinterStatus("✅ All barcode tests completed successfully!")
                    showToast("All barcodes printed!")
                } else {
                    updatePrinterStatus("❌ Barcode test failed: ${result.exceptionOrNull()?.message}")
                    showToast("Some barcodes failed!")
                }

            } catch (e: Exception) {
                updatePrinterStatus("❌ Barcode test error: ${e.message}")
                Log.e(TAG, "Barcode test failed", e)
            }
        }
    }

    private fun checkPrinterStatus() {
        if (!printerInitialized) {
            showToast("Printer not initialized yet")
            return
        }

        lifecycleScope.launch {
            try {
                updatePrinterStatus("📊 Checking printer status...")

                val connectionState = printerDriver.connectionState.value
                val status = printerDriver.printerStatus.value

                val statusReport = buildString {
                    appendLine("📊 PRINTER STATUS REPORT")
                    appendLine("Connection: $connectionState")
                    if (status != null) {
                        appendLine("Ready: ${if (status.isReady) "YES" else "NO"}")
                        appendLine("Paper: ${if (!status.noPaper) "OK" else "EMPTY"}")
                        appendLine("Operational: ${if (status.isOperational) "YES" else "NO"}")
                        appendLine("Firmware: ${status.firmwareVersion ?: "Unknown"}")
                    } else {
                        appendLine("Status: Information unavailable")
                    }
                }

                updatePrinterStatus(statusReport.toString())
                showToast("Status check completed")

            } catch (e: Exception) {
                updatePrinterStatus("❌ Status check failed: ${e.message}")
                Log.e(TAG, "Status check failed", e)
            }
        }
    }

    // === COMPREHENSIVE TESTING ===

    private fun runComprehensiveTests() {
        showProgress(true)
        updateStatus("Running comprehensive hardware tests...")

        lifecycleScope.launch {
            try {
                val results = mutableListOf<String>()

                // Test locker system
                try {
                    val systemStatus = lockerController.getSystemStatus()
                    val onlineStations = systemStatus.values.count { it }
                    results.add("Locker System: $onlineStations/4 stations online")
                } catch (e: Exception) {
                    results.add("Locker System: ERROR - ${e.message}")
                }

                // Test scanner connection
                try {
                    val scannerState = BarcodeScanner.connectionState.value
                    results.add("Barcode Scanner: $scannerState")
                } catch (e: Exception) {
                    results.add("Barcode Scanner: ERROR - ${e.message}")
                }

                // Test printer
                try {
                    val printerState = printerDriver.connectionState.value
                    results.add("Thermal Printer: $printerState")
                } catch (e: Exception) {
                    results.add("Thermal Printer: ERROR - ${e.message}")
                }

                // Display comprehensive results
                val report = buildString {
                    appendLine("🔍 COMPREHENSIVE TEST RESULTS")
                    appendLine("=" * 30)
                    results.forEach { appendLine("• $it") }
                    appendLine()
                    appendLine("Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                    appendLine("=" * 30)
                }

                runOnUiThread {
                    tvSystemStatus.text = report
                    updateStatus("Comprehensive tests completed")
                    showToast("All tests completed!")
                }

                Log.i(TAG, "Comprehensive tests completed")

            } catch (e: Exception) {
                Log.e(TAG, "Comprehensive tests failed", e)
                updateStatus("Comprehensive tests failed: ${e.message}")
            } finally {
                showProgress(false)
            }
        }
    }

    private fun resetAllHardware() {
        showProgress(true)
        updateStatus("Resetting all hardware...")

        lifecycleScope.launch {
            try {
                // Clear all UI
                clearScanResults()
                etLockerId.setText("")
                tvSystemStatus.text = ""

                // Reset states
                updateLockerStatus("Hardware reset - ready for testing")
                updateScannerStatus("Hardware reset - ready for scanning")
                updatePrinterStatus("Hardware reset - ready for printing")

                updateStatus("All hardware reset completed")
                showToast("Hardware reset completed!")

            } catch (e: Exception) {
                Log.e(TAG, "Reset failed", e)
                updateStatus("Reset failed: ${e.message}")
            } finally {
                showProgress(false)
            }
        }
    }

    // === UTILITY METHODS ===

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

    private fun setButtonsEnabled(enabled: Boolean) {
        runOnUiThread {
            btnOpenLocker.isEnabled = enabled && lockerInitialized
            btnCheckLocker.isEnabled = enabled && lockerInitialized
            btnTestStation.isEnabled = enabled && lockerInitialized
            btnSystemDiagnostics.isEnabled = enabled && lockerInitialized

            btnScannerFocus.isEnabled = true // Always enabled
            btnClearScans.isEnabled = true // Always enabled
            btnScannerReconnect.isEnabled = enabled && scannerInitialized

            btnPrinterStatus.isEnabled = enabled && printerInitialized

            btnRunAllTests.isEnabled = enabled
            btnResetAll.isEnabled = true // Always enabled
        }
    }

    private fun showProgress(show: Boolean) {
        runOnUiThread {
            progressBar.visibility = if (show) ProgressBar.VISIBLE else ProgressBar.GONE
        }
    }

    private fun updateStatus(message: String) {
        Log.d(TAG, "Status: $message")
    }

    private fun updateLockerStatus(message: String) {
        runOnUiThread {
            tvLockerStatus.text = "$message\n\nTime: ${SimpleDateFormat("HH:mm:ss").format(Date())}"
        }
    }

    private fun updateScannerStatus(message: String) {
        runOnUiThread {
            tvScannerStatus.text = message
        }
    }

    private fun updatePrinterStatus(message: String) {
        runOnUiThread {
            tvPrinterStatus.text = message
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this@HardwareTestActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private operator fun String.times(count: Int): String = this.repeat(count)

    override fun onDestroy() {
        super.onDestroy()

        Log.i(TAG, "=== Hardware Test Activity Shutting Down ===")

        try {
            if (lockerInitialized) {
                lifecycleScope.launch {
                    lockerController.close()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error closing locker controller: ${e.message}")
        }

        try {
            if (scannerInitialized) {
                BarcodeScanner.shutdown()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down scanner: ${e.message}")
        }

        try {
            if (printerInitialized) {
                printerDriver.cleanup()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up printer: ${e.message}")
        }

        Log.i(TAG, "Hardware test activity destroyed")
    }
}