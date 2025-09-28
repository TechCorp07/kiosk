package com.blitztech.pudokiosk.ui.Technician

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

// Hardware component imports
import com.blitztech.pudokiosk.deviceio.rs485.RS485CommunicationTester
import com.blitztech.pudokiosk.deviceio.rs232.BarcodeScanner // Object, not class
import com.blitztech.pudokiosk.deviceio.printer.CustomTG2480HIIIDriver
import com.blitztech.pudokiosk.deviceio.rs485.RS485Driver
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber

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
    // === STM32L412 RS485 DRIVER COMPONENTS ===
    private lateinit var rs485Driver: RS485Driver

    // === RS485 COMMUNICATION TEST COMPONENTS ===
    private lateinit var rs485Tester: RS485CommunicationTester
    private lateinit var spSerialDevices: Spinner
    private lateinit var btnScanSerial: Button
    private lateinit var btnConnectSerial: Button
    private lateinit var btnDisconnectSerial: Button
    private lateinit var btnTestComm: Button
    private lateinit var btnSendRaw: Button
    private lateinit var btnStartListening: Button
    private lateinit var btnClearCommLog: Button
    private lateinit var etRawHexInput: EditText
    private lateinit var etCommStation: EditText
    private lateinit var etCommLock: EditText
    private lateinit var tvSerialStatus: TextView
    private lateinit var tvCommLog: TextView
    private lateinit var scrollCommLog: ScrollView
    private var serialDevices = listOf<RS485CommunicationTester.SerialDevice>()

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hardware_test)

        Log.i(TAG, "=== PUDO Kiosk Hardware Test Center Started ===")

        initializeViews()
        setupEventListeners()
        initializeHardware()
    }

    private fun initializeViews() {
        // RS485 Communication Test UI
        spSerialDevices = findViewById(R.id.spSerialDevices)
        btnScanSerial = findViewById(R.id.btnScanSerial)
        btnConnectSerial = findViewById(R.id.btnConnectSerial)
        btnDisconnectSerial = findViewById(R.id.btnDisconnectSerial)
        btnTestComm = findViewById(R.id.btnTestComm)
        btnSendRaw = findViewById(R.id.btnSendRaw)
        btnStartListening = findViewById(R.id.btnStartListening)
        btnClearCommLog = findViewById(R.id.btnClearCommLog)
        etRawHexInput = findViewById(R.id.etRawHexInput)
        etCommStation = findViewById(R.id.etCommStation)
        etCommLock = findViewById(R.id.etCommLock)
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

        setupSpinners()
    }

    private fun setupSpinners() {
        // Serial devices spinner (initially empty)
        val serialAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            mutableListOf<String>())
        serialAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spSerialDevices.adapter = serialAdapter
    }
    private fun setupEventListeners() {
        // RS485 Communication Events
        btnScanSerial.setOnClickListener { scanSerialDevices() }
        btnConnectSerial.setOnClickListener { connectToSelectedDevice() }
        btnDisconnectSerial.setOnClickListener { disconnectFromDevice() }
        btnTestComm.setOnClickListener { testBasicCommunication() }
        btnSendRaw.setOnClickListener { sendRawHexData() }
        btnStartListening.setOnClickListener {
            if (isListening) stopListening() else startListening()
        }
        btnClearCommLog.setOnClickListener { clearCommunicationLog() }

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
                initializeSTM32Driver()

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

    // === STM32L412 RS485 DRIVER METHODS ===

    private fun initializeSTM32Driver() {
        updateSerialStatus("🔌 Initializing STM32L412 RS485 Driver...")

        try {
            rs485Driver = RS485Driver(this)

            // Initialize with auto-connect (like BarcodeScanner.initialize)
            rs485Driver.initializeOnStartup(this)

            // Monitor connection state
            monitorRS485Connection()

            rs485Initialized = true
            updateSerialStatus("✅ STM32L412 driver initialized - Auto-connecting...")
            Log.i(TAG, "STM32L412 RS485 driver initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "STM32L412 driver initialization failed", e)
            updateSerialStatus("❌ STM32L412 init failed: ${e.message}")
        }
    }
    private fun monitorRS485Connection() {
        lifecycleScope.launch {
            rs485Driver.connectionState.collectLatest { state ->
                val statusMessage = when (state) {
                    RS485Driver.ConnectionState.DISCONNECTED -> {
                        // Stop listening if we were listening
                        if (isListening) {
                            stopListening()
                        }
                        "⚪ STM32L412 disconnected"
                    }
                    RS485Driver.ConnectionState.CONNECTING -> "🔄 STM32L412 connecting..."
                    RS485Driver.ConnectionState.CONNECTED -> {
                        // AUTO-START LISTENING when connected
                        if (!isListening) {
                            startListening()
                        }
                        "✅ STM32L412 connected - Auto-listening for responses!"
                    }
                    RS485Driver.ConnectionState.PERMISSION_DENIED -> "❌ USB permission denied"
                    RS485Driver.ConnectionState.ERROR -> {
                        // Stop listening on error
                        if (isListening) {
                            stopListening()
                        }
                        "❌ STM32L412 connection error"
                    }
                }

                runOnUiThread {
                    updateSerialStatus(statusMessage)

                    val isConnected = (state == RS485Driver.ConnectionState.CONNECTED)
                    btnTestComm.isEnabled = isConnected
                    btnSendRaw.isEnabled = isConnected

                    // Update listening button based on connection and listening state
                    updateListeningButton(isConnected)

                    if (isConnected) {
                        val deviceInfo = rs485Driver.getDeviceInfo()
                        showToast("STM32L412 ready - Auto-listening started!")
                        Log.i(TAG, "STM32L412 device info: $deviceInfo")
                    }
                }

                Log.i(TAG, "STM32L412 connection state: $state")
            }
        }
    }

    private fun updateListeningButton(isConnected: Boolean) {
        when {
            !isConnected -> {
                btnStartListening.text = "Start Listening"
                btnStartListening.isEnabled = false
                btnStartListening.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
            isListening -> {
                btnStartListening.text = "Stop Listening"
                btnStartListening.isEnabled = true
                btnStartListening.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            }
            else -> {
                btnStartListening.text = "Start Listening"
                btnStartListening.isEnabled = true
                btnStartListening.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }
        }
    }

    // === RS485 COMMUNICATION TEST METHODS ===

    private fun initializeRS485Tester() {
        rs485Tester = RS485CommunicationTester(this)
        rs485Initialized = true

        updateSerialStatus("RS485 Communication Tester initialized")
        updateCommLog("📋 RS485 Communication Tester ready")
        updateCommLog("📌 Click 'Scan Serial' to discover connected devices")

        // Set default values
        etCommStation.setText("1")
        etCommLock.setText("1")
        etRawHexInput.setText("90 06 05 00 01 03")

        Log.i(TAG, "RS485 Communication Tester initialized")
    }

    private fun scanSerialDevices() {
        showProgress(true)
        updateSerialStatus("🔍 Comprehensive device scanning...")

        lifecycleScope.launch {
            try {
                Log.i(TAG, "=== COMPREHENSIVE USB/SERIAL DEVICE SCAN ===")

                val scanResults = mutableListOf<String>()
                val logBuilder = StringBuilder()

                logBuilder.appendLine("🔍 COMPREHENSIVE DEVICE SCAN STARTED")
                logBuilder.appendLine("=" * 40)

                // 1. GET USB MANAGER
                val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

                // 3. SCAN USB SERIAL DRIVERS (Detected by usb-serial library)
                Log.d(TAG, "Scanning USB serial drivers...")
                logBuilder.appendLine("🔌 USB SERIAL DRIVERS:")

                val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
                Log.i(TAG, "Found ${availableDrivers.size} USB serial drivers")

                if (availableDrivers.isEmpty()) {
                    logBuilder.appendLine("❌ No USB serial drivers detected!")
                    Log.w(TAG, "No USB serial drivers found - RS485 adapter may not be compatible")
                } else {
                    availableDrivers.forEachIndexed { index, driver ->
                        val device = driver.device
                        val driverInfo = buildString {
                            append("Driver ${index + 1}: ")
                            append("${driver.javaClass.simpleName} ")
                            append("Device:${device.deviceName} ")
                            append("VID:${String.format("%04X", device.vendorId)} ")
                            append("PID:${String.format("%04X", device.productId)} ")
                            append("Ports:${driver.ports.size}")
                        }

                        Log.i(TAG, "Serial Driver: $driverInfo")
                        logBuilder.appendLine("  • $driverInfo")

                        // Check each port
                        driver.ports.forEachIndexed { portIndex, port ->
                            val portInfo = "    Port $portIndex: ${port.javaClass.simpleName} " +
                                    "PortNumber:${port.portNumber}"
                            Log.d(TAG, "  $portInfo")
                            logBuilder.appendLine("  $portInfo")
                        }

                        scanResults.add(driverInfo)
                        logBuilder.appendLine("")
                    }
                }

                // 7. UPDATE UI COMPONENTS
                updateSpinnerWithResults(scanResults, availableDrivers)

                // 8. UPDATE COMMUNICATION LOG
                updateCommLog("📋 DEVICE SCAN COMPLETED")
                updateCommLogFromScanResults(logBuilder.toString())

                // 9. FINAL LOG SUMMARY
                Log.i(TAG, "=== SCAN SUMMARY ===")
                Log.i(TAG, "USB Serial Drivers: ${availableDrivers.size}")
                Log.i(TAG, "Compatible Devices: ${scanResults.size}")
                Log.i(TAG, "=== END SCAN ===")

            } catch (e: Exception) {
                val errorMsg = "❌ Error during device scan: ${e.message}"
                updateSerialStatus(errorMsg)
                updateCommLog(errorMsg)
                Log.e(TAG, "Device scan error", e)
            } finally {
                showProgress(false)
            }
        }
    }

    private fun connectToSelectedDevice() {
        val selectedIndex = spSerialDevices.selectedItemPosition
        val selectedText = spSerialDevices.selectedItem as? String ?: ""

        Log.d(TAG, "Selected device index: $selectedIndex")
        Log.d(TAG, "Selected device text: $selectedText")

        if (selectedIndex < 0) {
            updateSerialStatus("❌ Please select a device first")
            return
        }

        showProgress(true)
        updateSerialStatus("🔌 Attempting connection to: ${selectedText.take(50)}...")

        lifecycleScope.launch {
            try {
                // Check if this is a recognized serial device/port
                if (selectedText.startsWith("✅ SERIAL:") && selectedIndex < serialDevices.size) {
                    val selectedDevice = serialDevices[selectedIndex]
                    Log.d(TAG, "Connecting to serial device: ${selectedDevice.deviceInfo}")

                    // For multi-port devices, we need to specify which port to use
                    val portNumber = extractPortNumber(selectedText)
                    Log.d(TAG, "Extracted port number: $portNumber")

                    val connected = rs485Tester.connectToDevice(selectedDevice, portNumber = portNumber)

                    if (connected) {
                        updateSerialStatus("✅ Connected successfully!")
                        updateSerialStatus("📡 Device: ${selectedDevice.deviceName}")
                        updateSerialStatus("🔌 Port: $portNumber")
                        updateSerialStatus("⚙️ Baud: 9600, 8N1")

                        btnConnectSerial.isEnabled = false
                        btnDisconnectSerial.isEnabled = true
                        btnTestComm.isEnabled = true
                        btnSendRaw.isEnabled = true
                        btnStartListening.isEnabled = true

                        updateCommLog("✅ CONNECTION ESTABLISHED")
                        updateCommLog("📡 Ready for RS485 communication testing")
                        updateCommLog("🎯 Try 'Test Basic' with Station:1 Lock:1")
                    } else {
                        updateSerialStatus("❌ Failed to connect - check device permissions")
                    }

                } else {
                    updateSerialStatus("❌ Invalid selection")
                }

                updateCommLogFromTester()

            } catch (e: Exception) {
                updateSerialStatus("❌ Connection error: ${e.message}")
                Log.e(TAG, "Connection error", e)
                updateCommLog("❌ ERROR: ${e.message}")
            } finally {
                showProgress(false)
            }
        }
    }

    private fun extractPortNumber(deviceText: String): Int {
        try {
            // Look for "Port X" pattern
            val portPattern = "Port (\\d+)".toRegex()
            val match = portPattern.find(deviceText)

            if (match != null) {
                val portNum = match.groupValues[1].toInt()
                Log.d(TAG, "Extracted port number: $portNum")
                return portNum - 1 // Convert to 0-based index
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting port number: ${e.message}")
        }

        return 0 // Default to first port
    }
    private fun disconnectFromDevice() {
        lifecycleScope.launch {
            try {
                rs485Tester.disconnect()
                updateSerialStatus("🔌 Disconnected")

                btnConnectSerial.isEnabled = true
                btnDisconnectSerial.isEnabled = false
                btnTestComm.isEnabled = false
                btnSendRaw.isEnabled = false
                btnStartListening.isEnabled = false

                updateCommLogFromTester()

            } catch (e: Exception) {
                updateSerialStatus("❌ Error disconnecting: ${e.message}")
                Log.e(TAG, "Error disconnecting", e)
            }
        }
    }

    private fun testBasicCommunication() {
        if (!rs485Tester.isConnected()) {
            updateSerialStatus("❌ Not connected - connect to a device first")
            return
        }

        showProgress(true)
        updateSerialStatus("📡 Testing basic communication...")

        lifecycleScope.launch {
            try {
                val station = etCommStation.text.toString().toIntOrNull() ?: 1
                val lock = etCommLock.text.toString().toIntOrNull() ?: 1

                val success = rs485Tester.sendBasicTest(station, lock)

                if (success) {
                    updateSerialStatus("✅ Communication test successful!")
                } else {
                    updateSerialStatus("⚠️ No response received - check connections")
                }

                updateCommLogFromTester()

            } catch (e: Exception) {
                updateSerialStatus("❌ Communication test failed: ${e.message}")
                Log.e(TAG, "Error in communication test", e)
            } finally {
                showProgress(false)
            }
        }
    }

    private fun sendRawHexData() {
        if (!rs485Tester.isConnected()) {
            updateSerialStatus("❌ Not connected - connect to a device first")
            return
        }

        val hexInput = etRawHexInput.text.toString().trim()
        if (hexInput.isEmpty()) {
            updateSerialStatus("❌ Please enter hex data to send")
            return
        }

        showProgress(true)
        updateSerialStatus("📤 Sending raw hex data...")

        lifecycleScope.launch {
            try {
                val success = rs485Tester.sendRawHex(hexInput)

                if (success) {
                    updateSerialStatus("✅ Raw data sent and response received")
                } else {
                    updateSerialStatus("⚠️ Data sent but no response")
                }

                updateCommLogFromTester()

            } catch (e: Exception) {
                updateSerialStatus("❌ Error sending raw data: ${e.message}")
                Log.e(TAG, "Error sending raw data", e)
            } finally {
                showProgress(false)
            }
        }
    }

    private fun startListening() {
        if (rs485Driver.connectionState.value != RS485Driver.ConnectionState.CONNECTED) {
            updateSerialStatus("❌ STM32L412 not connected")
            return
        }

        if (isListening) {
            Log.d(TAG, "Already listening to STM32L412")
            return
        }

        try {
            Log.d(TAG, "Starting STM32L412 direct listening...")

            isListening = true
            updateListeningButton(true)
            updateSerialStatus("👂 Listening for STM32L412 responses...")

            // Add initial log message
            updateCommLog("👂 Started listening for STM32L412 data...")
            updateCommLog("📡 Device: ${rs485Driver.getDeviceInfo()}")
            updateCommLog("⏰ ${getCurrentTimestamp()}")
            updateCommLog("=" * 40)

            // Start continuous listening using the connected RS485Driver
            lifecycleScope.launch(Dispatchers.IO) {
                startDirectListening()
            }

            Log.i(TAG, "STM32L412 direct listening started")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start STM32L412 listening", e)
            updateSerialStatus("❌ Failed to start listening: ${e.message}")
            isListening = false
            updateListeningButton(rs485Driver.connectionState.value == RS485Driver.ConnectionState.CONNECTED)
        }
    }

    private fun stopListening() {
        if (!isListening) {
            Log.d(TAG, "Not currently listening to STM32L412")
            return
        }

        try {
            Log.d(TAG, "Stopping STM32L412 listener...")

            isListening = false
            updateListeningButton(rs485Driver.connectionState.value == RS485Driver.ConnectionState.CONNECTED)
            updateSerialStatus("⏹️ Stopped listening to STM32L412")

            // Add stop message to log
            updateCommLog("⏹️ Stopped listening at ${getCurrentTimestamp()}")
            updateCommLog("=" * 40)

            Log.i(TAG, "STM32L412 listening stopped")

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping STM32L412 listening", e)
            updateSerialStatus("❌ Error stopping listening: ${e.message}")
        }
    }

    private suspend fun startDirectListening() = withContext(Dispatchers.IO) {
        updateCommLog("🔄 Direct listening mode active...")

        while (isListening && rs485Driver.connectionState.value == RS485Driver.ConnectionState.CONNECTED) {
            try {
                // Use the RS485Driver's readData method or direct port access
                val receivedData = rs485Driver.readRawData(100) // 100ms timeout

                if (receivedData.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        handleReceivedData(receivedData)
                    }
                }

                delay(50) // Small delay to prevent tight loop

            } catch (e: Exception) {
                if (isListening) { // Only log if we're still supposed to be listening
                    Log.w(TAG, "Error during direct listening: ${e.message}")
                    withContext(Dispatchers.Main) {
                        updateCommLog("⚠️ Listening error: ${e.message}")
                    }
                }
                delay(200) // Longer delay on error
            }
        }

        withContext(Dispatchers.Main) {
            updateCommLog("👂 Direct listening stopped")
        }
    }

    private fun handleReceivedData(data: ByteArray) {
        val timestamp = getCurrentTimestamp()
        val hexString = data.joinToString(" ") { "%02X".format(it) }

        updateCommLog("📥 [$timestamp] Received ${data.size} bytes:")
        updateCommLog("   HEX: $hexString")

        // Try to parse as Winnsen protocol
        if (data.size >= 6 && data[0] == 0x90.toByte()) {
            parseWinnsenResponse(data)
        } else {
            // Try ASCII interpretation
            val asciiData = data.toString(Charsets.UTF_8).filter {
                it.isLetterOrDigit() || it.isWhitespace() || it in ".,!?-_"
            }
            if (asciiData.isNotEmpty()) {
                updateCommLog("   ASCII: '$asciiData'")
            }
        }

        updateCommLog("") // Empty line for readability
    }

    private fun parseWinnsenResponse(data: ByteArray) {
        try {
            when (data[2]) {
                0x85.toByte() -> { // Unlock response
                    if (data.size >= 7) {
                        val station = data[3].toInt() and 0xFF
                        val lock = data[4].toInt() and 0xFF
                        val status = data[5].toInt() and 0xFF
                        updateCommLog("   🔓 UNLOCK RESPONSE:")
                        updateCommLog("      Station: $station, Lock: $lock")
                        updateCommLog("      Result: ${if (status == 1) "SUCCESS (Door Open)" else "FAILED (Door Closed)"}")
                    }
                }
                0x92.toByte() -> { // Status response
                    if (data.size >= 7) {
                        val station = data[3].toInt() and 0xFF
                        val status = data[4].toInt() and 0xFF
                        val lock = data[5].toInt() and 0xFF
                        updateCommLog("   📋 STATUS RESPONSE:")
                        updateCommLog("      Station: $station, Lock: $lock")
                        updateCommLog("      Door: ${if (status == 1) "OPEN" else "CLOSED"}")
                    }
                }
                else -> {
                    updateCommLog("   📦 Winnsen Protocol (Function: 0x${"%02X".format(data[2])})")
                }
            }
        } catch (e: Exception) {
            updateCommLog("   ⚠️ Error parsing Winnsen response: ${e.message}")
        }
    }

    private fun updateCommLog(message: String) {
        runOnUiThread {
            val currentText = tvCommLog.text.toString()
            val newText = if (currentText.isEmpty() || currentText == "Click 'Scan Serial' to discover connected devices") {
                message
            } else {
                "$currentText\n$message"
            }

            tvCommLog.text = newText

            // Auto-scroll to bottom
            scrollCommLog.post {
                scrollCommLog.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
    }

    private fun clearCommunicationLog() {
        tvCommLog.text = ""
        updateCommLog("🧹 Communication log cleared")
        updateCommLog("⏰ ${getCurrentTimestamp()}")
        updateSerialStatus("🧹 Log cleared")
    }

    private fun updateCommLogFromTester() {
        val logMessages = rs485Tester.getLogMessages()
        val logText = logMessages.joinToString("\n")

        runOnUiThread {
            tvCommLog.text = logText
            // Scroll to bottom
            scrollCommLog.post {
                scrollCommLog.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    private fun updateSpinnerWithResults(
        scanResults: List<String>,
        availableDrivers: List<UsbSerialDriver>
    ) {
        runOnUiThread {
            try {
                Log.d(TAG, "=== ENHANCED CDC/MULTI-PORT DEVICE SCAN ===")

                val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
                val allUsbDevices = usbManager.deviceList

                Log.d(TAG, "Total USB devices found: ${allUsbDevices.size}")
                Log.d(TAG, "Recognized serial drivers: ${availableDrivers.size}")

                val allDeviceItems = mutableListOf<String>()
                val allSerialDevices = mutableListOf<RS485CommunicationTester.SerialDevice>()

                // First, add ALL ports from recognized serial drivers
                availableDrivers.forEach { driver ->
                    val device = driver.device

                    Log.d(TAG, "Processing driver: ${driver.javaClass.simpleName} with ${driver.ports.size} ports")

                    // Add each port separately
                    driver.ports.forEachIndexed { portIndex, port ->
                        val deviceInfo = buildString {
                            append("✅ SERIAL: ")
                            append("${device.deviceName} - ")
                            append("Port ${portIndex + 1}/${driver.ports.size} - ")
                            append("VID:${String.format("%04X", device.vendorId)} ")
                            append("PID:${String.format("%04X", device.productId)} - ")
                            append(identifyDeviceType(device.vendorId, device.productId, device.manufacturerName, device.productName))

                            // Add CDC identification
                            if (isCdcDevice(device)) {
                                append(" [CDC]")
                            }
                        }

                        allDeviceItems.add(deviceInfo)

                        // Create a serial device object for this specific port
                        allSerialDevices.add(RS485CommunicationTester.SerialDevice(
                            device = device,
                            driver = driver,
                            deviceInfo = deviceInfo,
                            vendorId = String.format("%04X", device.vendorId),
                            productId = String.format("%04X", device.productId),
                            deviceName = "${device.deviceName}_Port${portIndex + 1}"
                        ))

                        Log.d(TAG, "Added serial port: $deviceInfo")
                    }
                }

                // Update serialDevices list for connection
                serialDevices = allSerialDevices

                // Create adapter
                val displayItems = if (allDeviceItems.isEmpty()) {
                    listOf("❌ No USB devices detected")
                } else {
                    allDeviceItems
                }

                val newAdapter = ArrayAdapter(
                    this@HardwareTestActivity,
                    android.R.layout.simple_spinner_item,
                    displayItems
                )
                newAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spSerialDevices.adapter = newAdapter
                btnConnectSerial.isEnabled = allDeviceItems.isNotEmpty()

                Log.d(TAG, "Populated spinner with ${displayItems.size} total device ports")

            } catch (e: Exception) {
                Log.e(TAG, "Error in enhanced CDC scan: ${e.message}", e)
            }
        }
    }

    private fun isCdcDevice(device: UsbDevice): Boolean {
        try {
            // Check device class (CDC devices can have class 2)
            if (device.deviceClass == 2) return true

            // Check interfaces for CDC classes
            for (i in 0 until device.interfaceCount) {
                try {
                    val intf = device.getInterface(i)
                    // CDC Communication Interface Class = 2
                    // CDC Data Interface Class = 10
                    if (intf.interfaceClass == 2 || intf.interfaceClass == 10) {
                        return true
                    }
                } catch (e: Exception) {
                    continue
                }
            }

            // Check product name for CDC indicators
            device.productName?.let { productName ->
                if (productName.contains("CDC", ignoreCase = true) ||
                    productName.contains("Communication", ignoreCase = true) ||
                    productName.contains("Serial", ignoreCase = true)) {
                    return true
                }
            }

            return false
        } catch (e: Exception) {
            return false
        }
    }
    /**
     * Update communication log with scan results
     */
    private fun updateCommLogFromScanResults(scanText: String) {
        runOnUiThread {
            // Split into lines and add to log
            scanText.lines().forEach { line ->
                if (line.trim().isNotEmpty()) {
                    updateCommLog(line)
                }
            }

            // Scroll to bottom
            scrollCommLog.post {
                scrollCommLog.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    private fun identifyDeviceType(vendorId: Int, productId: Int, manufacturerName: String?, productName: String?): String {
        val vid = String.format("%04X", vendorId)
        val pid = String.format("%04X", productId)

        return when {
            // STMicroelectronics devices
            vendorId == 0x0483 -> "🎯 STM32/STMicroelectronics"

            // CDC-specific identification
            productName?.contains("CDC", ignoreCase = true) == true -> "📡 CDC Serial Device"

            // Common USB-Serial adapters used with RS485
            vendorId == 0x0403 && productId == 0x6001 -> "🔌 FTDI FT232R"
            vendorId == 0x0403 && productId == 0x6011 -> "🔌 FTDI FT4232H"
            vendorId == 0x10C4 && productId == 0xEA60 -> "🔌 CP2102 USB-UART"
            vendorId == 0x1A86 && productId == 0x7523 -> "🔌 CH340 USB-Serial"
            vendorId == 0x067B && productId == 0x2303 -> "🔌 PL2303 USB-Serial"

            // Generic patterns
            manufacturerName?.contains("FTDI", ignoreCase = true) == true -> "🔌 FTDI Device"
            productName?.contains("USB", ignoreCase = true) == true &&
                    productName?.contains("Serial", ignoreCase = true) == true -> "🔌 USB-Serial"

            else -> "❓ Unknown (VID:$vid PID:$pid)"
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
                if (rs485Initialized && rs485Tester.isConnected()) {
                    rs485Tester.disconnect()
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
            btnScanSerial.isEnabled = enabled && rs485Initialized
            btnConnectSerial.isEnabled = enabled && rs485Initialized && serialDevices.isNotEmpty() && !rs485Tester.isConnected()
            btnDisconnectSerial.isEnabled = enabled && rs485Initialized && rs485Tester.isConnected()
            btnTestComm.isEnabled = enabled && rs485Initialized && rs485Tester.isConnected()
            btnSendRaw.isEnabled = enabled && rs485Initialized && rs485Tester.isConnected()
            btnStartListening.isEnabled = enabled && rs485Initialized && rs485Tester.isConnected()
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
            Log.d(TAG, "Serial Status: $message")
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
            if (rs485Initialized) {
                lifecycleScope.launch {
                    rs485Tester.disconnect()
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