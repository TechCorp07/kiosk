package com.blitztech.pudokiosk.deviceio.rs232

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import com.blitztech.pudokiosk.usb.UsbHelper
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.nio.charset.Charset

/**
 * Minimal RS232 barcode reader using usb-serial-for-android.
 * Assumes the scanner sends ASCII with CR/LF terminator.
 */
class BarcodeScanner1900(private val ctx: Context, private val preferredBaud: Int = 9600) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val usbHelper = UsbHelper(ctx)
    private var port: UsbSerialPort? = null
    private var readJob: Job? = null

    private val _scans = MutableSharedFlow<String>(
        replay = 0, extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val scans: SharedFlow<String> = _scans

    fun start() {
        usbHelper.register()
        scope.launch {
            tryOpenPort()
            startReader()
        }
    }

    fun stop() {
        readJob?.cancel()
        readJob = null
        try { port?.close() } catch (_: Exception) {}
        port = null
        usbHelper.unregister()
    }

    private fun tryOpenPort() {
        val usb = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usb)
        if (drivers.isEmpty()) { Log.w(TAG, "No USB serial drivers found"); return }

        // Heuristic: pick first serial port that is NOT already opened elsewhere.
        val driver: UsbSerialDriver = drivers.first()
        val device = driver.device

        if (!usbHelper.hasPermission(device)) {
            usbHelper.requestPermission(device) { d, granted ->
                if (granted) {
                    // permission callback happens on main; reopen from IO
                    scope.launch { tryOpenPort() }
                } else {
                    Log.e(TAG, "USB permission denied for scanner")
                }
            }
            return
        }

        val connection = usb.openDevice(device) ?: run {
            Log.e(TAG, "openDevice() returned null")
            return
        }

        val p = driver.ports.firstOrNull()
        if (p == null) { connection.close(); Log.e(TAG, "No ports on driver"); return }

        port = p.apply {
            open(connection)
            setParameters(preferredBaud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            dtr = true; rts = true
        }
        Log.i(TAG, "Scanner port opened @ $preferredBaud baud")
    }

    private fun startReader() {
        val p = port ?: return
        readJob?.cancel()
        readJob = scope.launch {
            val buf = ByteArray(128)
            val line = StringBuilder()
            while (isActive) {
                try {
                    val n = p.read(buf, 250)
                    if (n > 0) {
                        for (i in 0 until n) {
                            val b = buf[i].toInt() and 0xFF
                            if (b == 0x0D || b == 0x0A) { // CR or LF
                                if (line.isNotEmpty()) {
                                    val text = line.toString().trim()
                                    line.clear()
                                    if (text.isNotEmpty()) {
                                        Log.d(TAG, "SCAN: $text")
                                        _scans.emit(text)
                                    }
                                }
                            } else {
                                line.append(b.toChar())
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Scanner read error: ${e.message}")
                    delay(300)
                }
            }
        }
    }

    companion object { private const val TAG = "BarcodeScanner1900" }
}
