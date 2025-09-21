package com.blitztech.pudokiosk.deviceio.rs485

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SerialRs485(private val ctx: Context) {
    private var port: UsbSerialPort? = null

    suspend fun open(baud: Int = 9600) = withContext(Dispatchers.IO) {
        if (port != null) return@withContext
        val usb = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usb)
        require(availableDrivers.isNotEmpty()) { "No USB serial drivers found" }
        val driver: UsbSerialDriver = availableDrivers.first()
        val connection = usb.openDevice(driver.device)
            ?: throw IllegalStateException("No permission to open USB device")

        port = driver.ports.first().apply {
            open(connection)
            setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            dtr = true; rts = true
        }
    }

    suspend fun writeRead(frame: ByteArray, readBytes: Int, timeoutMs: Int = 300): ByteArray =
        withContext(Dispatchers.IO) {
            val p = port ?: throw IllegalStateException("Serial port not open")
            p.write(frame, timeoutMs)
            val buf = ByteArray(readBytes)
            val n = p.read(buf, timeoutMs)
            if (n <= 0) throw IllegalStateException("No response")
            buf.copyOf(n)
        }

    suspend fun close() = withContext(Dispatchers.IO) {
        try { port?.close() } catch (_: Exception) { }
        port = null
    }
}
