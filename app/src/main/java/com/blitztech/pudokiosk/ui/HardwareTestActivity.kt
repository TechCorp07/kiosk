package com.blitztech.pudokiosk.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.data.AuditLogger
import com.blitztech.pudokiosk.deviceio.printer.CustomTG2480HIIIDriver
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class HardwareTestActivity : AppCompatActivity() {

    // === Printer Components (Corrected Custom API Implementation) ===
    private lateinit var customPrinter: CustomTG2480HIIIDriver

    // === UI Components ===
    private lateinit var btnPrintTest: Button
    private lateinit var btnPrinterStatus: Button
    private lateinit var btnPrintReceipt: Button
    private lateinit var tvPrinterStatus: TextView
    private lateinit var tvPrinterDetails: TextView

    // === Keep Your Existing Hardware Components Unchanged ===
    // private var serialPort: UsbSerialPort? = null
    // private lateinit var barcodeScanner: BarcodeScanner1900
    // private lateinit var lockerController: LockerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hardware_test)

        // Set up UI elements
        setupPrinterUI()

        // Initialize the corrected printer driver
        initializePrinterDriver()

        // Keep your existing hardware initialization
        // initializeOtherHardware()
    }

    /**
     * Initialize the corrected Custom API printer driver
     *
     * This method now uses the driver that follows the real Custom API patterns
     * discovered from your demo files, ensuring proper compatibility.
     */
    private fun initializePrinterDriver() {
        // Create the corrected printer driver
        customPrinter = CustomTG2480HIIIDriver(
            context = this,
            simulate = false, // Set to true for testing without hardware
            enableAutoReconnect = true
        )

        // Initialize the driver asynchronously
        lifecycleScope.launch {
            try {
                showPrinterStatus("Initializing printer driver...")

                val result = customPrinter.initialize()
                if (result.isSuccess) {
                    showPrinterStatus("✓ Custom printer driver initialized successfully")
                    AuditLogger.log("INFO", "PRINTER_INIT_SUCCESS", "Corrected API driver ready")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    showPrinterStatus("✗ Printer initialization failed: $error")
                    AuditLogger.log("ERROR", "PRINTER_INIT_FAIL", "msg=$error")
                }
            } catch (e: Exception) {
                showPrinterStatus("✗ Printer driver error: ${e.message}")
                AuditLogger.log("ERROR", "PRINTER_INIT_EXCEPTION", "msg=${e.message}")
            }
        }

        // Monitor connection state changes using the corrected driver
        lifecycleScope.launch {
            customPrinter.connectionState.collect { state ->
                val stateMessage = when (state) {
                    CustomTG2480HIIIDriver.ConnectionState.DISCONNECTED -> "⚪ Printer disconnected"
                    CustomTG2480HIIIDriver.ConnectionState.CONNECTING -> "🟡 Connecting to printer..."
                    CustomTG2480HIIIDriver.ConnectionState.CONNECTED -> "🟢 Printer connected and ready"
                    CustomTG2480HIIIDriver.ConnectionState.ERROR -> "🔴 Printer connection error"
                    CustomTG2480HIIIDriver.ConnectionState.PERMISSION_DENIED -> "🔴 USB permission denied"
                    CustomTG2480HIIIDriver.ConnectionState.DEVICE_NOT_FOUND -> "🔴 Printer not found"
                    CustomTG2480HIIIDriver.ConnectionState.RECONNECTING -> "🟡 Reconnecting..."
                }

                withContext(Dispatchers.Main) {
                    showPrinterStatus(stateMessage)
                }

                AuditLogger.log("INFO", "PRINTER_STATE_CHANGE", "state=$state")
            }
        }

        // Monitor detailed printer status using the corrected status structure
        lifecycleScope.launch {
            customPrinter.printerStatus.collect { status ->
                status?.let {
                    withContext(Dispatchers.Main) {
                        updatePrinterDetails(it)
                    }
                }
            }
        }
    }

    /**
     * Set up printer-related UI components
     */
    private fun setupPrinterUI() {
        // Initialize UI components
        btnPrintTest = findViewById(R.id.btnPrintTest)
        btnPrinterStatus = findViewById(R.id.btnPrinterStatus)
        btnPrintReceipt = findViewById(R.id.btnPrintReceipt)
        tvPrinterStatus = findViewById(R.id.tvPrinterStatus)
        tvPrinterDetails = findViewById(R.id.tvPrinterDetails)

        // Set up button click handlers
        btnPrintTest.setOnClickListener { testBasicPrinting() }
        btnPrinterStatus.setOnClickListener { checkPrinterStatus() }
        btnPrintReceipt.setOnClickListener { testReceiptPrinting() }
    }

    private fun testBasicPrinting() {
        lifecycleScope.launch {
            try {
                showToast("Starting basic print test...")

                val result1  = customPrinter.printText("KIOSK PRINTER TEST", fontSize = 2, bold = true, centered = true)
                val result2  = customPrinter.printText("=" * 32)
                val result3  = customPrinter.printText("")
                val result4  = customPrinter.printText("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                val result5  = customPrinter.printText("Hardware: Custom TG2480HIII")
                val result6  = customPrinter.printText("API: Custom Android API (Corrected)", bold = true)
                val result7  = customPrinter.printText("Android: ${android.os.Build.VERSION.RELEASE}")
                val result8  = customPrinter.printText("")
                val result9  = customPrinter.printText("This test demonstrates:", bold = true)
                val result10 = customPrinter.printText("✓ Corrected API integration")
                val result11 = customPrinter.printText("✓ Proper PrinterFont usage")
                val result12 = customPrinter.printText("✓ Real exception handling")
                val result13 = customPrinter.printText("✓ Accurate status checking")
                val result14 = customPrinter.printText("")
                val result15 = customPrinter.printText("Test completed successfully!", bold = true)
                val result16 = customPrinter.printText("=" * 32)

                val allResults = listOf(
                    result1,result2,result3,result4,result5,result6,
                    result7,result8,result9,result10,result11,result12,
                    result13,result14,result15,result16
                )

                if (allResults.all { it.isSuccess }) {
                    showToast("✓ Basic print test successful!")
                    AuditLogger.log("INFO", "PRINT_TEST_SUCCESS", "Corrected API test completed")
                } else {
                    val failedCount = allResults.count { it.isFailure }
                    showToast("✗ Print test partially failed ($failedCount failures)")
                    AuditLogger.log("WARNING", "PRINT_TEST_PARTIAL_FAIL", "failed_lines=$failedCount")
                }
            } catch (e: Exception) {
                showToast("Print test error: ${e.message}")
                AuditLogger.log("ERROR", "PRINT_TEST_EXCEPTION", "msg=${e.message}")
            }
        }
    }

    private fun checkPrinterStatus() {
        lifecycleScope.launch {
            try {
                showToast("Checking printer status...")

                val statusResult = customPrinter.getCurrentStatus()
                if (statusResult.isSuccess) {
                    val status = statusResult.getOrThrow()

                    val inferredError: String? = when {
                        status.noPaper -> "No paper"
                        !status.isReady -> "Printer not ready"
                        else -> null
                    }

                    val statusReport = buildString {
                        appendLine("DETAILED PRINTER STATUS")
                        appendLine("=" * 30)
                        appendLine("Ready: ${if (status.isReady) "✓ YES" else "✗ NO"}")
                        appendLine("Paper Present: ${if (!status.noPaper) "✓ YES" else "✗ NO/LOW"}")
                        appendLine("Paper Rolling: ${if (status.paperRolling) "✓ YES" else "✗ NO"}")
                        appendLine("LF Pressed: ${if (status.lfPressed) "✓ YES" else "✗ NO"}")
                        appendLine("Operational: ${if (status.isOperational) "✓ YES" else "✗ NO"}")

                        status.printerName?.let { appendLine("Printer: $it") }
                        status.firmwareVersion?.let { appendLine("Firmware: $it") }
                        inferredError?.let { appendLine("Error: $it") }

                        appendLine("Last Updated: ${
                            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(status.lastUpdated))
                        }")
                    }

                    showToast(statusReport)
                    AuditLogger.log("INFO", "PRINTER_STATUS_CHECK", "operational=${status.isOperational}")
                } else {
                    val error = statusResult.exceptionOrNull()?.message ?: "Unknown error"
                    showToast("Status check failed: $error")
                    AuditLogger.log("ERROR", "PRINTER_STATUS_FAIL", "msg=$error")
                }
            } catch (e: Exception) {
                showToast("Status check error: ${e.message}")
                AuditLogger.log("ERROR", "PRINTER_STATUS_EXCEPTION", "msg=${e.message}")
            }
        }
    }

    private fun testReceiptPrinting() {
        lifecycleScope.launch {
            try {
                showToast("Printing formatted receipt...")

                // Sample transaction data
                val header = "PUDO KIOSK SERVICES"
                val items = listOf(
                    "Package Delivery" to "$5.00",
                    "Priority Handling" to "$2.50",
                    "SMS Notification" to "$0.50",
                    "Insurance (Optional)" to "$1.00",
                    "Service Fee" to "$1.25",
                    "Tax (15%)" to "$1.54"
                )
                val footer = buildString {
                    appendLine("Total: $11.79")
                    appendLine()
                    appendLine("Thank you for using")
                    appendLine("PUDO Kiosk Services!")
                    appendLine()
                    appendLine("Transaction ID: ${System.currentTimeMillis()}")
                    appendLine("${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                }

                val result = customPrinter.printReceipt(header, items, footer, cutPaper = true)

                if (result.isSuccess) {
                    showToast("✓ Receipt printed successfully!")
                    AuditLogger.log("INFO", "RECEIPT_PRINT_SUCCESS", "Corrected API receipt completed")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    showToast("✗ Receipt printing failed: $error")
                    AuditLogger.log("ERROR", "RECEIPT_PRINT_FAIL", "msg=$error")
                }

            } catch (e: Exception) {
                showToast("Receipt printing error: ${e.message}")
                AuditLogger.log("ERROR", "RECEIPT_PRINT_EXCEPTION", "msg=${e.message}")
            }
        }
    }

    /**
     * Update printer status display in real-time
     */
    private fun showPrinterStatus(message: String) {
        runOnUiThread {
            tvPrinterStatus.text = message
        }
    }

    /**
     * Update detailed printer information display using corrected status structure
     */
    private fun updatePrinterDetails(status: CustomTG2480HIIIDriver.CustomPrinterStatus) {
        val details = buildString {
            append("Ready: ${if (status.isReady) "✓" else "✗"} | ")
            append("Paper: ${if (!status.noPaper) "✓" else "✗"} | ")
            append("Rolling: ${if (status.paperRolling) "↻" else "○"} | ")
            append("LF: ${if (status.lfPressed) "✓" else "✗"}")

            if (!status.isOperational) append(" | CHECK")
        }
        tvPrinterDetails.text = details
    }

    /**
     * Display toast messages safely on main thread
     */
    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@HardwareTestActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Extension function for string repetition
     */
    private operator fun String.times(count: Int): String {
        return this.repeat(count)
    }

    /**
     * Clean up all resources when activity is destroyed
     *
     * This includes the corrected printer driver shutdown process
     */
    override fun onDestroy() {
        super.onDestroy()

        // Clean up the corrected printer driver
        customPrinter.shutdown()

        // Keep your existing cleanup code for other hardware
        // barcodeScanner.stop()
        // serialPort?.close()
        // ... other existing cleanup

        AuditLogger.log("INFO", "HARDWARE_TEST_ACTIVITY_DESTROYED", "All resources cleaned up")
    }
}