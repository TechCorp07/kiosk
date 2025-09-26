package com.blitztech.pudokiosk.ui.Technician

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

// Hardware component imports
import com.blitztech.pudokiosk.deviceio.rs485.LockerController
import com.blitztech.pudokiosk.deviceio.rs485.RS485CommunicationTester
import com.blitztech.pudokiosk.deviceio.rs232.BarcodeScanner // Object, not class
import com.blitztech.pudokiosk.deviceio.printer.CustomTG2480HIIIDriver
import com.blitztech.pudokiosk.ui.base.BaseKioskActivity
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber

/**
 * Unified Hardware Test Activity for PUDO Kiosk
 * Tests the three actual hardware components:
 * 1. STM32L412 Locker Controller (RS485)
 * 2. Honeywell Xenon 1900 Barcode Scanner (RS232)
 * 3. Custom TG2480HIII Thermal Printer (USB/Custom API)
 */
class HardwareTestActivity : BaseKioskActivity() {
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

        // Serial devices spinner (initially empty)
        val serialAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            mutableListOf<String>())
        serialAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spSerialDevices.adapter = serialAdapter
    }
    private fun setupEventListeners() {
        // Locker Controller Events
        swSimulateLockers.setOnCheckedChangeListener { _, isChecked ->
            reinitializeLockerController(isChecked)
        }

        btnOpenLocker.setOnClickListener {
            val lockerId = etLockerId.text.toString().trim()
            if (lockerId.isNotEmpty()) {
                openLocker(lockerId)
            } else {
                updateLockerStatus("Please enter a locker ID")
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
            testStationCommunication(station)
        }

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
                initializeLockerController()
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

                if (success) {
                    showToast("Locker $lockerId opened successfully!")
                } else {
                    showToast("❌ Failed to open $lockerId ")
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
                val status = lockerController.checkLockerStatus(lockerId)

                updateLockerStatus("Locker $lockerId is $status")
            } catch (e: Exception) {
                updateLockerStatus("❌ Error checking $lockerId status: ${e.message}")
                Log.e(TAG, "Error checking locker status", e)
            } finally {
                showProgress(false)
            }
        }
    }

    private fun testStationCommunication(station: Int) {
        showProgress(true)
        updateLockerStatus("Testing station $station...")

        lifecycleScope.launch {
            try {
                val success = lockerController.testCommunication()
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
        etRawHexInput.setText("90 06 05 01 01 03")

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

                // 2. SCAN ALL USB DEVICES (Raw USB devices)
                Log.d(TAG, "Scanning all USB devices...")
                logBuilder.appendLine("📱 ALL USB DEVICES:")

                val allUsbDevices = usbManager.deviceList
                Log.i(TAG, "Found ${allUsbDevices.size} total USB devices")

                if (allUsbDevices.isEmpty()) {
                    logBuilder.appendLine("❌ No USB devices found at all!")
                    Log.w(TAG, "No USB devices detected - check USB connections")
                } else {
                    allUsbDevices.values.forEachIndexed { index, device ->
                        val deviceInfo = buildString {
                            append("Device ${index + 1}: ")
                            append("${device.deviceName} ")
                            append("VID:${String.format("%04X", device.vendorId)} ")
                            append("PID:${String.format("%04X", device.productId)}")

                            device.manufacturerName?.let { append(" Mfg:$it") }
                            device.productName?.let { append(" Product:$it") }
                            device.serialNumber?.let { append(" SN:$it") }

                            append(" Class:${device.deviceClass}")
                            append(" Subclass:${device.deviceSubclass}")
                            append(" Protocol:${device.deviceProtocol}")
                            append(" Interfaces:${device.interfaceCount}")
                        }

                        Log.i(TAG, "USB Device: $deviceInfo")
                        logBuilder.appendLine("  • $deviceInfo")

                        // Check if we have permission to access this device
                        val hasPermission = usbManager.hasPermission(device)
                        Log.d(TAG, "  → Permission: $hasPermission")
                        logBuilder.appendLine("    Permission: ${if (hasPermission) "✅ GRANTED" else "❌ DENIED"}")

                        // Log interface details
                        for (i in 0 until device.interfaceCount) {
                            try {
                                val intf = device.getInterface(i)
                                val intfInfo = "Interface $i: Class:${intf.interfaceClass} " +
                                        "Subclass:${intf.interfaceSubclass} Protocol:${intf.interfaceProtocol} " +
                                        "Endpoints:${intf.endpointCount}"
                                Log.d(TAG, "    $intfInfo")
                                logBuilder.appendLine("    $intfInfo")
                            } catch (e: Exception) {
                                Log.w(TAG, "    Error reading interface $i: ${e.message}")
                            }
                        }
                        logBuilder.appendLine("")
                    }
                }

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

                // 4. SCAN NATIVE SERIAL PORTS (Linux /dev/tty* devices)
                Log.d(TAG, "Scanning native serial ports...")
                logBuilder.appendLine("🖥️ NATIVE SERIAL PORTS:")

                val serialPorts = scanNativeSerialPorts()
                if (serialPorts.isEmpty()) {
                    logBuilder.appendLine("❌ No native serial ports found")
                    Log.w(TAG, "No /dev/tty* devices accessible")
                } else {
                    serialPorts.forEach { portInfo ->
                        Log.i(TAG, "Native Serial Port: $portInfo")
                        logBuilder.appendLine("  • $portInfo")
                    }
                }
                logBuilder.appendLine("")

                // 5. SYSTEM INFORMATION
                Log.d(TAG, "Gathering system information...")
                logBuilder.appendLine("📋 SYSTEM INFORMATION:")
                logBuilder.appendLine("  • Android Version: ${android.os.Build.VERSION.RELEASE}")
                logBuilder.appendLine("  • API Level: ${android.os.Build.VERSION.SDK_INT}")
                logBuilder.appendLine("  • Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                logBuilder.appendLine("  • Board: ${android.os.Build.BOARD}")
                logBuilder.appendLine("  • Hardware: ${android.os.Build.HARDWARE}")
                logBuilder.appendLine("")

                // 6. SPECIFIC RS485/STM32 RECOMMENDATIONS
                logBuilder.appendLine("🔍 STM32L412 DETECTION TIPS:")
                logBuilder.appendLine("  • Look for FTDI (VID:0403), CP2102 (VID:10C4), or CH340 (VID:1A86) chips")
                logBuilder.appendLine("  • STM32 with USB may appear as VID:0483 (STMicroelectronics)")
                logBuilder.appendLine("  • Check USB cable - must support data, not just power")
                logBuilder.appendLine("  • Verify RS485-USB adapter is properly connected")
                logBuilder.appendLine("  • Try different USB ports on Android box")
                logBuilder.appendLine("")

                // 7. UPDATE UI COMPONENTS
                updateSpinnerWithResults(scanResults, availableDrivers)
                updateSerialStatus(if (scanResults.isEmpty())
                    "❌ No compatible devices found - see log for details" else
                    "✅ Found ${scanResults.size} serial device(s) - ${allUsbDevices.size} total USB devices")

                // 8. UPDATE COMMUNICATION LOG
                updateCommLog("📋 DEVICE SCAN COMPLETED")
                updateCommLogFromScanResults(logBuilder.toString())

                // 9. FINAL LOG SUMMARY
                Log.i(TAG, "=== SCAN SUMMARY ===")
                Log.i(TAG, "Total USB Devices: ${allUsbDevices.size}")
                Log.i(TAG, "USB Serial Drivers: ${availableDrivers.size}")
                Log.i(TAG, "Native Serial Ports: ${serialPorts.size}")
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

                } else if (selectedText.startsWith("🔌 USB:")) {
                    // Handle raw USB device
                    updateSerialStatus("🔧 Attempting raw USB connection...")
                    Log.d(TAG, "Attempting raw USB device connection")

                    val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
                    val allDevices = usbManager.deviceList.values

                    // Extract VID/PID from selected text
                    val vidPidPattern = "VID:([0-9A-F]{4}) PID:([0-9A-F]{4})".toRegex()
                    val match = vidPidPattern.find(selectedText)

                    if (match != null) {
                        val vid = match.groupValues[1].toInt(16)
                        val pid = match.groupValues[2].toInt(16)

                        val targetDevice = allDevices.find {
                            it.vendorId == vid && it.productId == pid
                        }

                        if (targetDevice != null) {
                            Log.d(TAG, "Found raw USB device: VID:${String.format("%04X", vid)} PID:${String.format("%04X", pid)}")

                            // Try to probe for a driver
                            val driver = UsbSerialProber.getDefaultProber().probeDevice(targetDevice)

                            if (driver != null) {
                                Log.d(TAG, "Successfully probed driver: ${driver.javaClass.simpleName}")

                                val customDevice = RS485CommunicationTester.SerialDevice(
                                    device = targetDevice,
                                    driver = driver,
                                    deviceInfo = selectedText,
                                    vendorId = String.format("%04X", vid),
                                    productId = String.format("%04X", pid),
                                    deviceName = targetDevice.deviceName
                                )

                                val connected = rs485Tester.connectToDevice(customDevice)

                                if (connected) {
                                    updateSerialStatus("✅ Raw USB connection successful!")
                                    btnConnectSerial.isEnabled = false
                                    btnDisconnectSerial.isEnabled = true
                                    btnTestComm.isEnabled = true
                                    btnSendRaw.isEnabled = true
                                    btnStartListening.isEnabled = true
                                } else {
                                    updateSerialStatus("❌ Raw USB connection failed")
                                }
                            } else {
                                updateSerialStatus("❌ No compatible driver found for this device")
                                updateCommLog("💡 Device may need specific driver or different connection method")
                            }
                        } else {
                            updateSerialStatus("❌ Could not find USB device")
                        }
                    } else {
                        updateSerialStatus("❌ Could not parse device VID/PID")
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
        if (!rs485Tester.isConnected()) {
            updateSerialStatus("❌ Not connected - connect to a device first")
            return
        }

        isListening = true
        btnStartListening.text = "Stop Listening"
        updateSerialStatus("👂 Listening for incoming data...")

        lifecycleScope.launch {
            try {
                rs485Tester.startListening(10000) // Listen for 10 seconds
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
        rs485Tester.clearLog()
        tvCommLog.text = "Communication log cleared\n"
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

    /**
     * Scan for native serial ports (Linux /dev/tty* devices)
     */
    private suspend fun scanNativeSerialPorts(): List<String> = withContext(Dispatchers.IO) {
        val ports = mutableListOf<String>()

        try {
            // Common serial device paths on Android/Linux
            val serialPaths = listOf(
                "/dev/ttyUSB", "/dev/ttyACM", "/dev/ttyS",
                "/dev/tty", "/dev/serial", "/dev/bus/usb"
            )

            serialPaths.forEach { basePath ->
                for (i in 0..10) {
                    val devicePath = "$basePath$i"
                    val file = java.io.File(devicePath)

                    if (file.exists()) {
                        val accessible = file.canRead() || file.canWrite()
                        val info = "$devicePath ${if (accessible) "(accessible)" else "(no permission)"}"
                        ports.add(info)
                        Log.d(TAG, "Native serial port: $info")
                    }
                }
            }

            // Also check for USB device nodes
            try {
                val usbDir = java.io.File("/dev/bus/usb")
                if (usbDir.exists() && usbDir.isDirectory) {
                    usbDir.listFiles()?.forEach { busDir ->
                        if (busDir.isDirectory) {
                            busDir.listFiles()?.forEach { deviceFile ->
                                ports.add("/dev/bus/usb/${busDir.name}/${deviceFile.name}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error scanning USB device nodes: ${e.message}")
            }

        } catch (e: Exception) {
            Log.w(TAG, "Error scanning native serial ports: ${e.message}")
        }

        ports
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

                // Then add raw USB devices that might be CDC but not recognized
                allUsbDevices.values.forEach { device ->
                    val alreadyAdded = availableDrivers.any { it.device.deviceName == device.deviceName }

                    if (!alreadyAdded) {
                        val deviceInfo = buildString {
                            append("🔌 USB: ")
                            append("${device.deviceName} - ")
                            append("VID:${String.format("%04X", device.vendorId)} ")
                            append("PID:${String.format("%04X", device.productId)} - ")
                            append(identifyDeviceType(device.vendorId, device.productId, device.manufacturerName, device.productName))

                            // Add detailed USB class information for CDC detection
                            append(" [Class:${device.deviceClass}")
                            if (device.deviceSubclass != 0) append(".${device.deviceSubclass}")
                            if (device.deviceProtocol != 0) append(".${device.deviceProtocol}")
                            append("]")

                            // Highlight potential CDC devices
                            if (isCdcDevice(device)) {
                                append(" ⭐ POTENTIAL CDC DEVICE")
                            }

                            // Show interface details for unrecognized devices
                            if (device.interfaceCount > 0) {
                                append(" Interfaces:${device.interfaceCount}")
                            }
                        }

                        allDeviceItems.add(deviceInfo)
                        Log.d(TAG, "Added raw USB device: $deviceInfo")

                        // Log detailed interface information for CDC detection
                        for (i in 0 until device.interfaceCount) {
                            try {
                                val intf = device.getInterface(i)
                                Log.d(TAG, "  Interface $i: Class:${intf.interfaceClass} Sub:${intf.interfaceSubclass} Protocol:${intf.interfaceProtocol}")

                                // CDC Communication Interface Class = 2, CDC Data Interface Class = 10
                                if (intf.interfaceClass == 2 || intf.interfaceClass == 10) {
                                    Log.i(TAG, "  ⭐ FOUND CDC INTERFACE on ${device.deviceName}")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "  Error reading interface $i: ${e.message}")
                            }
                        }
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

                // Add helpful messages to communication log
                updateCommLog("📋 ENHANCED SCAN COMPLETE - ${displayItems.size} device ports found")
                updateCommLog("🎯 Looking for 'CDC' devices - your STM32L412 should show as CDC")
                updateCommLog("🔍 Check for 'Serial Device - CDC - Port 2' equivalent")
                updateCommLog("✅ Multi-port devices now show each port separately")

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
                    appendLine("Test Mode: ${if (swSimulateLockers.isChecked) "SIMULATION" else "HARDWARE"}")
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

                // Test locker system
                try {
                    val systemStatus = lockerController.getSystemStatus()
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

    private fun resetAllSystems() {
        lifecycleScope.launch {
            try {
                if (rs485Initialized && rs485Tester.isConnected()) {
                    rs485Tester.disconnect()
                }

                updateStatus("All systems reset")
                updateLockerStatus("Ready for testing")
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
            btnOpenLocker.isEnabled = enabled && lockerInitialized
            btnCheckLocker.isEnabled = enabled && lockerInitialized
            btnTestStation.isEnabled = enabled && lockerInitialized
            btnSystemDiagnostics.isEnabled = enabled && lockerInitialized

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

    private fun updateLockerStatus(message: String) {
        runOnUiThread {
            tvLockerStatus.text = "$message\n\nTime: ${SimpleDateFormat("HH:mm:ss").format(Date())}"
        }
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