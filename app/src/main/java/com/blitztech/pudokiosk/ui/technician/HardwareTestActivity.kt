package com.blitztech.pudokiosk.ui.technician

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

// Hardware component imports
import com.blitztech.pudokiosk.deviceio.rs232.BarcodeScanner // Object, not class
import com.blitztech.pudokiosk.deviceio.printer.CustomTG2480HIIIDriver
import com.blitztech.pudokiosk.deviceio.rs485.LockerController
import com.blitztech.pudokiosk.deviceio.rs485.RS485Driver
import com.blitztech.pudokiosk.deviceio.rs485.WinnsenProtocol
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity

/**
 * Tests the three actual hardware components:
 * 1. STM32L412 Locker Controller (RS485)
 * 2. Honeywell Xenon 1900 Barcode Scanner (RS232)
 * 3. Custom TG2480HIII Thermal Printer (USB/Custom API)
 */
class HardwareTestActivity : BaseKioskActivity() {
    companion object {
        private const val TAG = "HardwareTest"
    }

    // === RS485 COMMUNICATION TEST COMPONENTS ===
    private lateinit var rs485Driver: RS485Driver
    private lateinit var btnStartListening: Button
    private lateinit var btnClearCommLog: Button
    private lateinit var tvSerialStatus: TextView
    private lateinit var tvCommLog: TextView
    private lateinit var scrollCommLog: ScrollView
    private lateinit var lockerController: LockerController
    private lateinit var btnConnectLocker: Button
    private lateinit var btnTestCommunication: Button
    private lateinit var btnUnlockSingle: Button
    private lateinit var btnCheckStatus: Button
    private lateinit var btnCheckAllStatus: Button
    private lateinit var btnClearLockerLog: Button
    private lateinit var btnEmergencyUnlock: Button

    //private lateinit var etLockNumber: EditText
    private lateinit var tvLockerStatus: TextView
    private lateinit var tvLockerLog: TextView
    private lateinit var scrollLockerLog: ScrollView
    private lateinit var spinnerLockNumber: Spinner

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
    private var rs485Initialized = false
    private var scannerInitialized = false
    private var printerInitialized = false
    private var isListening = false
    private var lockerConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hardware_test)

        Log.i(TAG, "=== PUDO Kiosk Hardware Test Center Started ===")

        initializeViews()
        initializeHardware()
        setupEventListeners()
        debugLayoutComponents()
    }

    private fun initializeViews() {
        // RS485 Communication Test UI
        btnConnectLocker = findViewById(R.id.btnConnectLocker)
        btnTestCommunication = findViewById(R.id.btnTestCommunication)
        btnUnlockSingle = findViewById(R.id.btnUnlockSingle)
        btnCheckStatus = findViewById(R.id.btnCheckStatus)
        btnCheckAllStatus = findViewById(R.id.btnCheckAllStatus)
        btnEmergencyUnlock = findViewById(R.id.btnEmergencyUnlock)
        btnClearLockerLog = findViewById(R.id.btnClearLockerLog)

        //etLockNumber = findViewById(R.id.etLockNumber)
        tvLockerStatus = findViewById(R.id.tvLockerStatus)
        tvLockerLog = findViewById(R.id.tvLockerLog)
        scrollLockerLog = findViewById(R.id.scrollLockerLog)
        spinnerLockNumber = findViewById(R.id.spinnerLockNumber)
        btnStartListening = findViewById(R.id.btnStartListening)
        btnClearCommLog = findViewById(R.id.btnClearCommLog)
        tvSerialStatus = findViewById(R.id.tvSerialStatus)
        tvCommLog = findViewById(R.id.tvCommLog)
        scrollCommLog = findViewById(R.id.scrollCommLog)

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

        initializeLockerController()
    }

    private fun setupEventListeners() {
        // RS485 Communication Events
        btnStartListening.setOnClickListener {
            if (isListening) stopListening() else startListening()
        }
        btnClearCommLog.setOnClickListener { clearCommunicationLog() }
        btnConnectLocker.setOnClickListener { connectToLockerController() }
        btnTestCommunication.setOnClickListener { testLockerCommunication() }
        btnUnlockSingle.setOnClickListener { unlockSingleLock() }
        btnCheckStatus.setOnClickListener { checkSingleLockStatus() }
        btnCheckAllStatus.setOnClickListener { checkAllLockStatuses() }
        btnEmergencyUnlock.setOnClickListener { emergencyUnlockAll() }

        // Scanner Events
        btnScannerFocus.setOnClickListener { etScannerResults.requestFocus() }
        btnTriggerScan.setOnClickListener { triggerBarcodeScan() }
        btnClearScans.setOnClickListener { etScannerResults.text.clear() }
        btnScannerReconnect.setOnClickListener { reconnectScanner() }

        // Printer Events
        btnPrintTest.setOnClickListener { testBasicPrinting() }
        btnPrintReceipt.setOnClickListener { printSampleReceipt() }
        btnPrintBarcode.setOnClickListener { printBarcodeTest() }
        btnPrinterStatus.setOnClickListener { checkPrinterStatus() }

        // General Events
        btnRunAllTests.setOnClickListener { runComprehensiveTests() }
        btnResetAll.setOnClickListener { resetAllSystems() }

        // Initially disable buttons until connected
        btnTestCommunication.isEnabled = false
        btnUnlockSingle.isEnabled = false
        btnCheckStatus.isEnabled = false
        btnCheckAllStatus.isEnabled = false
        btnEmergencyUnlock.isEnabled = false
    }

    private fun initializeHardware() {
        showProgress(true)
        updateStatus("Initializing hardware components...")

        lifecycleScope.launch {
            try {
                // Initialize all hardware components
                initializeRS485Tester()
                initializeBarcodeScanner()
                initializeThermalPrinter()

                updateStatus("All hardware components initialized")
                setButtonsEnabled(true)

            } catch (e: Exception) {
                Log.e(TAG, "Hardware initialization failed", e)
                updateStatus("Hardware initialization failed: ${e.message}")
            } finally {
                showProgress(false)
            }
        }
    }

    private fun initializeLockerController() {
        lockerController = LockerController(this)
        updateLockerStatus("🔒 Locker Controller ready - Click 'Connect' to start")

        // Setup lock number spinner
        val lockNumbers = (1..16).map { "Lock $it" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, lockNumbers)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLockNumber.adapter = adapter

        Log.i(TAG, "Locker Controller initialized")
    }

    // === RS485 COMMUNICATION TEST METHODS ===
    private fun connectToLockerController() {
        if (lockerConnected) {
            disconnectFromLockerController()
            return
        }

        updateLockerStatus("🔄 Connecting to STM32L412 Locker Controller...")
        showProgress(true)

        lifecycleScope.launch {
            try {
                val success = lockerController.connect()

                if (success) {
                    lockerConnected = true
                    btnConnectLocker.text = "Disconnect"
                    updateLockerStatus("✅ Connected to Locker Controller")

                    // Enable other buttons
                    btnTestCommunication.isEnabled = true
                    btnUnlockSingle.isEnabled = true
                    btnCheckStatus.isEnabled = true
                    btnCheckAllStatus.isEnabled = true
                    btnEmergencyUnlock.isEnabled = true

                    updateLockerLogFromController()
                    showToast("Locker Controller connected!")

                } else {
                    updateLockerStatus("❌ Failed to connect to Locker Controller")
                    showToast("Connection failed!")
                }

            } catch (e: Exception) {
                updateLockerStatus("❌ Connection error: ${e.message}")
                Log.e(TAG, "Locker connection error", e)
                showToast("Connection error!")

            } finally {
                showProgress(false)
            }
        }
    }

    private fun disconnectFromLockerController() {
        lifecycleScope.launch {
            try {
                lockerController.disconnect()
                lockerConnected = false
                btnConnectLocker.text = "Connect"
                updateLockerStatus("🔌 Disconnected from Locker Controller")

                // Disable other buttons
                btnTestCommunication.isEnabled = false
                btnUnlockSingle.isEnabled = false
                btnCheckStatus.isEnabled = false
                btnCheckAllStatus.isEnabled = false
                btnEmergencyUnlock.isEnabled = false

            } catch (e: Exception) {
                Log.e(TAG, "Disconnect error", e)
            }
        }
    }

    private fun testLockerCommunication() {
        if (!lockerConnected) {
            updateLockerStatus("❌ Not connected")
            return
        }

        updateLockerStatus("🧪 Testing communication...")
        showProgress(true)

        lifecycleScope.launch {
            try {
                val success = lockerController.testCommunication()

                if (success) {
                    updateLockerStatus("✅ Communication test successful!")
                    showToast("Communication test passed!")
                } else {
                    updateLockerStatus("❌ Communication test failed!")
                    showToast("Communication test failed!")
                }

                updateLockerLogFromController()

            } catch (e: Exception) {
                updateLockerStatus("❌ Test error: ${e.message}")
                Log.e(TAG, "Communication test error", e)

            } finally {
                showProgress(false)
            }
        }
    }

    private fun unlockSingleLock() {
        if (!lockerConnected) {
            updateLockerStatus("❌ Not connected")
            return
        }

        val selectedPosition = spinnerLockNumber.selectedItemPosition
        val lockNumber = selectedPosition + 1 // Locks are 1-16

        updateLockerStatus("🔓 Unlocking lock $lockNumber...")
        showProgress(true)

        lifecycleScope.launch {
            try {
                val result = lockerController.unlockLock(lockNumber)

                if (result.success) {
                    updateLockerStatus("✅ Lock $lockNumber unlocked successfully! Status: ${result.status.displayName}")
                    showToast("Lock $lockNumber unlocked!")
                } else {
                    updateLockerStatus("❌ Failed to unlock lock $lockNumber: ${result.errorMessage}")
                    showToast("Unlock failed!")
                }

                updateLockerLogFromController()

            } catch (e: Exception) {
                updateLockerStatus("❌ Unlock error: ${e.message}")
                Log.e(TAG, "Unlock error", e)

            } finally {
                showProgress(false)
            }
        }
    }

    private fun checkSingleLockStatus() {
        if (!lockerConnected) {
            updateLockerStatus("❌ Not connected")
            return
        }

        val selectedPosition = spinnerLockNumber.selectedItemPosition
        val lockNumber = selectedPosition + 1

        updateLockerStatus("🔍 Checking status of lock $lockNumber...")
        showProgress(true)

        lifecycleScope.launch {
            try {
                val result = lockerController.checkLockStatus(lockNumber)

                if (result.success) {
                    val statusIcon = when (result.status) {
                        WinnsenProtocol.LockStatus.OPEN -> "🔓"
                        WinnsenProtocol.LockStatus.CLOSED -> "🔒"
                        else -> "❓"
                    }
                    updateLockerStatus("$statusIcon Lock $lockNumber status: ${result.status.displayName}")
                    showToast("Lock $lockNumber: ${result.status.displayName}")
                } else {
                    updateLockerStatus("❌ Failed to check lock $lockNumber: ${result.errorMessage}")
                    showToast("Status check failed!")
                }

                updateLockerLogFromController()

            } catch (e: Exception) {
                updateLockerStatus("❌ Status check error: ${e.message}")
                Log.e(TAG, "Status check error", e)

            } finally {
                showProgress(false)
            }
        }
    }

    private fun checkAllLockStatuses() {
        if (!lockerConnected) {
            updateLockerStatus("❌ Not connected")
            return
        }

        updateLockerStatus("🔍 Checking status of all locks...")
        showProgress(true)

        lifecycleScope.launch {
            try {
                val results = lockerController.checkAllLockStatuses()

                val openLocks = results.filter { it.value.status == WinnsenProtocol.LockStatus.OPEN }
                val closedLocks = results.filter { it.value.status == WinnsenProtocol.LockStatus.CLOSED }
                val unknownLocks = results.filter { it.value.status == WinnsenProtocol.LockStatus.UNKNOWN }

                val summary = "📊 Status Summary: ${openLocks.size} open, ${closedLocks.size} closed, ${unknownLocks.size} unknown"
                updateLockerStatus(summary)

                // Show detailed results in a dialog
                showLockStatusDialog(results)

                updateLockerLogFromController()

            } catch (e: Exception) {
                updateLockerStatus("❌ Status check error: ${e.message}")
                Log.e(TAG, "All status check error", e)

            } finally {
                showProgress(false)
            }
        }
    }

    private fun emergencyUnlockAll() {
        if (!lockerConnected) {
            updateLockerStatus("❌ Not connected")
            return
        }

        // Show confirmation dialog for emergency unlock
        AlertDialog.Builder(this)
            .setTitle("⚠️ Emergency Unlock")
            .setMessage("This will attempt to unlock ALL 16 locks. Continue?")
            .setPositiveButton("YES, UNLOCK ALL") { _, _ ->
                performEmergencyUnlock()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performEmergencyUnlock() {
        updateLockerStatus("🚨 EMERGENCY: Unlocking all locks...")
        showProgress(true)

        lifecycleScope.launch {
            try {
                val results = lockerController.emergencyUnlockAll()

                val successCount = results.values.count { it.success }
                val failureCount = results.size - successCount

                val summary = "🚨 Emergency unlock completed: $successCount succeeded, $failureCount failed"
                updateLockerStatus(summary)

                showToast("Emergency unlock: $successCount/16 locks unlocked")

                // Show detailed results
                showLockStatusDialog(results)

                updateLockerLogFromController()

            } catch (e: Exception) {
                updateLockerStatus("❌ Emergency unlock error: ${e.message}")
                Log.e(TAG, "Emergency unlock error", e)

            } finally {
                showProgress(false)
            }
        }
    }

    private fun showLockStatusDialog(results: Map<Int, WinnsenProtocol.LockOperationResult>) {
        val message = StringBuilder()
        message.append("Lock Status Details:\n\n")

        for (lockNumber in 1..16) {
            val result = results[lockNumber]
            val statusIcon = when (result?.status) {
                WinnsenProtocol.LockStatus.OPEN -> "🔓"
                WinnsenProtocol.LockStatus.CLOSED -> "🔒"
                else -> "❓"
            }
            val statusText = result?.status?.displayName ?: "Unknown"
            message.append("Lock $lockNumber: $statusIcon $statusText\n")
        }

        AlertDialog.Builder(this)
            .setTitle("Lock Status Report")
            .setMessage(message.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updateLockerStatus(status: String) {
        runOnUiThread {
            tvLockerStatus.text = status
        }
    }

    private fun updateLockerLogFromController() {
        val logMessages = lockerController.getLogMessages()
        val logText = logMessages.joinToString("\n")

        runOnUiThread {
            tvLockerLog.text = logText
            // Scroll to bottom
            scrollLockerLog.post {
                scrollLockerLog.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    private fun initializeRS485Tester() {
        rs485Driver = RS485Driver(this)
        rs485Initialized = true

        lifecycleScope.launch {
            try {
                //rs485Driver.connect(baudRate = 9600, portNumber = 1)
                updateCommLogFromTester()

            } catch (e: Exception) {
                updateSerialStatus("❌ Connection error: ${e.message}")
                Log.e(TAG, "Connection error", e)
                updateCommLog("❌ ERROR: ${e.message}")
            } finally {
                showProgress(false)
            }
        }
        updateSerialStatus("RS485 Communication Tester initialized & Ready")
        updateCommLog("📌 Click 'Scan Serial' to discover connected devices")

        Log.i(TAG, "RS485 Communication Tester initialized")
    }

    private fun startListening() {
        if (!rs485Driver.isConnected()) {
            updateSerialStatus("❌ Not connected - connect to a device first")
            return
        }

        isListening = true
        btnStartListening.text = "Stop Listening"
        updateSerialStatus("👂 Listening for incoming data...")

        lifecycleScope.launch {
            try {
                rs485Driver.startListening(10000) // Listen for 10 seconds
                updateCommLogFromTester()

            } catch (e: Exception) {
                updateSerialStatus("❌ Error during listening: ${e.message}")
                Log.e(TAG, "Error during listening", e)
            } finally {
                stopListening()
            }
        }
    }

    private fun stopListening() {
        isListening = false
        btnStartListening.text = "Start Listening"
        updateSerialStatus("👂 Listening stopped")
    }

    private fun clearCommunicationLog() {
        rs485Driver.clearLog()
        tvCommLog.text = "Communication log cleared\n"
        updateSerialStatus("🧹 Log cleared")
    }

    private fun updateCommLogFromTester() {
        val logMessages = rs485Driver.getLogMessages()
        val logText = logMessages.joinToString("\n")

        runOnUiThread {
            tvCommLog.text = logText
            // Scroll to bottom
            scrollCommLog.post {
                scrollCommLog.fullScroll(ScrollView.FOCUS_DOWN)
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
                    printerDriver.feedAndCut()
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
                    appendLine("Thank you for testing!")
                    appendLine("PUDO Kiosk System v1.0")
                    appendLine("=" * 32)
                    appendLine()
                }

                val result = printerDriver.printText(receiptText, fontSize = 1, centered = false)

                if (result.isSuccess) {
                    printerDriver.feedAndCut()
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
                    printerDriver.feedAndCut()
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

                Log.i(TAG, "Comprehensive tests completed")

            } catch (e: Exception) {
                Log.e(TAG, "Comprehensive tests failed", e)
                updateStatus("Comprehensive tests failed: ${e.message}")
            } finally {
                showProgress(false)
            }
        }
    }

    private fun resetAllSystems() {
        lifecycleScope.launch {
            try {
                if (rs485Initialized && rs485Driver.isConnected()) {
                    rs485Driver.disconnect()
                }

                updateStatus("All systems reset")
                updateSerialStatus("Ready for testing")
                updateScannerStatus("Ready for testing")
                updatePrinterStatus("Ready for testing")

            } catch (e: Exception) {
                Log.e(TAG, "Error resetting systems", e)
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
            btnStartListening.isEnabled = true
            btnClearCommLog.isEnabled = enabled && rs485Initialized

            btnScannerFocus.isEnabled = true
            btnClearScans.isEnabled = true
            btnScannerReconnect.isEnabled = enabled && scannerInitialized

            btnPrinterStatus.isEnabled = enabled && printerInitialized

            btnRunAllTests.isEnabled = enabled
            btnResetAll.isEnabled = true
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

    private fun updateSerialStatus(message: String) {
        runOnUiThread {
            tvSerialStatus.text = "$message\n\nTime: ${SimpleDateFormat("HH:mm:ss").format(Date())}"
        }
    }

    private fun updateCommLog(message: String) {
        runOnUiThread {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS").format(Date())
            tvCommLog.append("[$timestamp] $message\n")

            scrollCommLog.post {
                scrollCommLog.fullScroll(ScrollView.FOCUS_DOWN)
            }
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

    private fun debugLayoutComponents() {
        Log.d(TAG, "=== LAYOUT COMPONENT DEBUG ===")

        // Check existing components
        Log.d(TAG, "RS485 components:")
        Log.d(TAG, "  btnStartListening: ${findViewById<Button>(R.id.btnStartListening) != null}")
        Log.d(TAG, "  btnClearCommLog: ${findViewById<Button>(R.id.btnClearCommLog) != null}")

        // Check locker components
        Log.d(TAG, "Locker components:")
        Log.d(TAG, "  btnConnectLocker: ${findViewById<Button>(R.id.btnConnectLocker) != null}")
        Log.d(TAG, "  btnTestCommunication: ${findViewById<Button>(R.id.btnTestCommunication) != null}")
        Log.d(TAG, "  spinnerLockNumber: ${findViewById<Spinner>(R.id.spinnerLockNumber) != null}")
        Log.d(TAG, "  tvLockerStatus: ${findViewById<TextView>(R.id.tvLockerStatus) != null}")

        Log.d(TAG, "=== END LAYOUT DEBUG ===")
    }

    private operator fun String.times(count: Int): String = this.repeat(count)

    override fun onDestroy() {
        super.onDestroy()

        Log.i(TAG, "=== Hardware Test Activity Shutting Down ===")

        try {
            if (rs485Initialized) {
                lifecycleScope.launch {
                    rs485Driver.disconnect()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error closing RS485 tester: ${e.message}")
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