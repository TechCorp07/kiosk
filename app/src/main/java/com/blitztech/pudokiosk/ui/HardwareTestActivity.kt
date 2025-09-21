package com.blitztech.pudokiosk.ui

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.*
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blitztech.pudokiosk.R
import com.blitztech.pudokiosk.data.ServiceLocator
import com.blitztech.pudokiosk.data.AuditLogger
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import android.os.Build


class HardwareTestActivity : AppCompatActivity() {

    private lateinit var tvUsb: TextView
    private lateinit var spSerial: Spinner
    private lateinit var spBaud: Spinner
    private lateinit var btnOpenSerial: Button
    private lateinit var btnSendProbe: Button
    private lateinit var btnOpenLocker: Button
    private lateinit var btnCheckLocker: Button
    private lateinit var etStation: EditText
    private lateinit var etLock: EditText

    private lateinit var btnPrintTest: Button
    private lateinit var btnScannerFocus: Button
    private lateinit var etScannerSink: EditText
    private lateinit var permIntent: PendingIntent

    private var serialPort: UsbSerialPort? = null
    private var serialConnection: UsbDeviceConnection? = null
    private val usbManager by lazy { getSystemService(Context.USB_SERVICE) as UsbManager }

    private val ACTION_USB_PERMISSION = "com.blitztech.pudokiosk.USB_PERMISSION"

    private val usbPermReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                Toast.makeText(this@HardwareTestActivity,
                    if (granted) "USB permission granted for ${device?.deviceName}" else "USB permission denied",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hardware_test)

        tvUsb = findViewById(R.id.tvUsb)
        spSerial = findViewById(R.id.spSerial)
        spBaud = findViewById(R.id.spBaud)
        btnOpenSerial = findViewById(R.id.btnOpenSerial)
        btnSendProbe = findViewById(R.id.btnSendProbe)
        btnOpenLocker = findViewById(R.id.btnOpenLocker)
        btnCheckLocker = findViewById(R.id.btnCheckLocker)
        etStation = findViewById(R.id.etStation)
        etLock = findViewById(R.id.etLock)

        btnPrintTest = findViewById(R.id.btnPrintTest)
        btnScannerFocus = findViewById(R.id.btnScannerFocus)
        etScannerSink = findViewById(R.id.etScannerSink)

        permIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // register runtime USB permission receiver
        @Suppress("UnspecifiedRegisterReceiverFlag") // keep lint happy on older APIs
        if (Build.VERSION.SDK_INT >= 33) {
            // Android 13+ requires an exported flag; keep it NOT_EXPORTED for our custom action
            registerReceiver(usbPermReceiver, IntentFilter(ACTION_USB_PERMISSION), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbPermReceiver, IntentFilter(ACTION_USB_PERMISSION))
        }

        // Fill baud spinner
        spBaud.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf(9600, 19200, 38400, 57600, 115200).map { it.toString() })

        refreshUsbList()

        btnOpenSerial.setOnClickListener { openSelectedSerial() }
        btnSendProbe.setOnClickListener { serialSendProbe() }

        btnOpenLocker.setOnClickListener { sendOpenLocker() }
        btnCheckLocker.setOnClickListener { sendCheckLocker() }

        btnPrintTest.setOnClickListener { printTestTicket() }

        btnScannerFocus.setOnClickListener {
            etScannerSink.requestFocus()
            Toast.makeText(this, "Scanner test: focus in textbox and scan (USB HID).", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(usbPermReceiver) } catch (_: Throwable) {}
        closeSerial()
    }

    private fun refreshUsbList() {
        // List all USB serial-capable drivers
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val items = drivers.flatMap { d -> d.ports.mapIndexed { idx, p -> Pair(d, idx) } }
        spSerial.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            items.map { (d, idx) -> "${d.device.vendorId}:${d.device.productId} port$idx" })

        // Show all attached USB devices for quick view
        val names = usbManager.deviceList.values.joinToString { "${it.vendorId}:${it.productId}" }
        tvUsb.text = if (names.isEmpty()) "USB: none" else "USB: $names"
    }

    private fun requestUsbPermission(device: UsbDevice) {
        if (!usbManager.hasPermission(device)) {
            usbManager.requestPermission(device, permIntent)
        }
    }

    private fun openSelectedSerial() {
        closeSerial()
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val selection = spSerial.selectedItemPosition
        if (selection < 0 || drivers.isEmpty()) { toast("No serial device"); return }

        // Determine driver+port from flattened list
        var idx = 0
        var chosenDriver = drivers.first()
        var chosenPort = 0
        loop@ for (d in drivers) {
            for (p in d.ports.indices) {
                if (idx == selection) { chosenDriver = d; chosenPort = p; break@loop }
                idx++
            }
        }

        val device = chosenDriver.device
        requestUsbPermission(device)
        serialConnection = usbManager.openDevice(device)
        if (serialConnection == null) { toast("Open device failed (permission?)"); return }

        serialPort = chosenDriver.ports[chosenPort]
        serialPort?.open(serialConnection)
        val baud = spBaud.selectedItem.toString().toInt()
        serialPort?.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        toast("Serial open @ $baud")
    }

    private fun closeSerial() {
        try { serialPort?.close() } catch (_: Throwable) {}
        serialPort = null
        try { serialConnection?.close() } catch (_: Throwable) {}
        serialConnection = null
    }

    private fun serialSendProbe() {
        val port = serialPort ?: return toast("Open serial first")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val probe = "HELLO\r\n".toByteArray()
                port.write(probe, 1000)
                val buf = ByteArray(64)
                val n = port.read(buf, 500)
                withContext(Dispatchers.Main) {
                    toast("Wrote ${probe.size} bytes, read ${if (n>0) n else 0}")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { toast("Probe failed: ${t.message}") }
            }
        }
    }

    // ===== Locker RS-485 test (through USB-Serial RS485 adapter) =====
    private fun sendOpenLocker() {
        val station = etStation.text.toString().toIntOrNull() ?: 1
        val lock = etLock.text.toString().toIntOrNull() ?: 1
        val frame = byteArrayOf(
            0x90.toByte(), 0x06, 0x05,
            station.toByte(), lock.toByte(),
            0x03
        )
        writeAndToast(frame, "Open sent; waiting reply…")
    }

    private fun sendCheckLocker() {
        val station = etStation.text.toString().toIntOrNull() ?: 1
        val lock = etLock.text.toString().toIntOrNull() ?: 1
        val frame = byteArrayOf(
            0x90.toByte(), 0x06, 0x12,
            station.toByte(), lock.toByte(),
            0x03
        )
        writeAndToast(frame, "Status sent; waiting reply…")
    }

    private fun writeAndToast(bytes: ByteArray, prefix: String) {
        val port = serialPort ?: return toast("Open serial first")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                port.write(bytes, 1000)
                val buf = ByteArray(64)
                val n = port.read(buf, 1000)
                val hex = buf.take(n.coerceAtLeast(0)).joinToString(" ") { b -> "%02X".format(b) }
                withContext(Dispatchers.Main) {
                    toast("$prefix got ${if (n>0) n else 0} bytes: $hex")
                    AuditLogger.log("INFO", "TECH_TEST_SERIAL", "wrote=${bytes.size} read=$n")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    toast("Serial write/read failed: ${t.message}")
                    AuditLogger.log("ERROR", "TECH_TEST_SERIAL_FAIL", "msg=${t.message}")
                }
            }
        }
    }

    // ===== Printer test over USB (Custom TG2480HIII) =====
    private fun printTestTicket() {
        // Find a Custom (vendor 3540) device and send a simple ESC/POS ticket to first bulk-OUT endpoint.
        val device = usbManager.deviceList.values.firstOrNull { it.vendorId == 3540 }
        if (device == null) { toast("Custom printer not found"); return }
        requestUsbPermission(device)
        val conn = usbManager.openDevice(device) ?: run { toast("No permission / open failed"); return }

        try {
            // Claim first interface having a bulk OUT endpoint
            val intf = (0 until device.interfaceCount)
                .map { device.getInterface(it) }
                .firstOrNull { it.endpointCount > 0 && (0 until it.endpointCount).any { e -> it.getEndpoint(e).type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.getEndpoint(e).direction == UsbConstants.USB_DIR_OUT } }
                ?: run { toast("No bulk OUT endpoint"); return }

            conn.claimInterface(intf, true)
            val out = (0 until intf.endpointCount)
                .map { intf.getEndpoint(it) }
                .first { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT }

            // Minimal ESC/POS-ish test
            val text = "TECH TEST\nPrinter OK\n\n"
            val cut = byteArrayOf(0x1D, 0x56, 0x00) // GS V 0  (partial-cut on many printers)
            val payload = text.toByteArray(Charset.forName("UTF-8")) + cut
            val sent = conn.bulkTransfer(out, payload, payload.size, 2000)
            toast("Print sent bytes=$sent")
            AuditLogger.log("INFO", "TECH_TEST_PRINT", "bytes=$sent")
        } catch (t: Throwable) {
            toast("Print failed: ${t.message}")
            AuditLogger.log("ERROR", "TECH_TEST_PRINT_FAIL", "msg=${t.message}")
        } finally {
            try { conn.close() } catch (_: Throwable) {}
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
