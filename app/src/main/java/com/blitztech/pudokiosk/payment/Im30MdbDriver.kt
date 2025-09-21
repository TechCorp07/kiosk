package com.blitztech.pudokiosk.payment

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import com.blitztech.pudokiosk.usb.UsbHelper
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import kotlin.math.min

/**
 * IM30 via custom MDB box on RS232.
 * Replace Codec with your real wire format (framing/CRC).
 */
class Im30MdbDriver(
    private val ctx: Context,
    private val baud: Int = 9600,
    private val simulate: Boolean = true,
    private val codec: Codec = AsciiCodec
): PaymentTerminal {

    interface Codec {
        fun encodePay(amountCents: Int): ByteArray
        fun parseResponse(bytes: ByteArray): PaymentResult?
        val terminator: ByteArray
    }

    object AsciiCodec : Codec {
        override fun encodePay(amountCents: Int): ByteArray =
            "PAY:$amountCents\r\n".toByteArray(Charset.forName("US-ASCII"))
        override val terminator: ByteArray = "\r\n".toByteArray(Charset.forName("US-ASCII"))
        override fun parseResponse(bytes: ByteArray): PaymentResult? {
            val s = String(bytes, Charsets.US_ASCII).trim()
            return when {
                s.equals("OK", true) -> PaymentResult.Approved()
                s.startsWith("ERR:", true) -> PaymentResult.Declined(s.substringAfter("ERR:").ifBlank { "DECLINED" })
                else -> null
            }
        }
    }

    private val usbHelper = UsbHelper(ctx)
    private var port: UsbSerialPort? = null

    override suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        if (simulate) return@withContext true
        usbHelper.register()
        try {
            val usb = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
            val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usb)
            if (drivers.isEmpty()) return@withContext false
            val driver: UsbSerialDriver = drivers.first()
            if (!usbHelper.hasPermission(driver.device)) {
                var granted = false
                val lock = java.lang.Object()
                usbHelper.requestPermission(driver.device) { _, ok -> synchronized(lock) { granted = ok; lock.notify() } }
                synchronized(lock) { if (!granted) lock.wait(2000) }
                if (!granted) return@withContext false
            }
            val conn = usb.openDevice(drivers.first().device) ?: return@withContext false
            val p = driver.ports.firstOrNull() ?: return@withContext false
            p.open(conn)
            p.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            p.dtr = true; p.rts = true
            port = p
            true
        } catch (t: Throwable) {
            Log.e("Im30Mdb", "init failed", t)
            false
        }
    }

    override suspend fun pay(req: PaymentRequest, timeoutMs: Long): PaymentResult = withContext(Dispatchers.IO) {
        if (simulate) {
            delay(500)
            return@withContext PaymentResult.Approved(authCode = "SIMOK")
        }
        val p = port ?: return@withContext PaymentResult.Error("Not initialized")
        val frame = codec.encodePay(req.amountCents)
        return@withContext try {
            p.write(frame, 1000)
            // read until terminator or timeout
            val buf = ByteArray(256); var total = 0
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeoutMs) {
                val n = p.read(buf, 250)
                if (n > 0) {
                    total = min(buf.size, total + n)
                    // scan for terminator
                    val idx = indexOf(buf, total, codec.terminator)
                    if (idx >= 0) {
                        val slice = buf.copyOfRange(0, idx)
                        return@withContext codec.parseResponse(slice) ?: PaymentResult.Error("Bad response")
                    }
                }
            }
            PaymentResult.Error("Timeout")
        } catch (t: Throwable) {
            PaymentResult.Error("IO error: ${t.message}")
        }
    }

    override suspend fun close() {
        withContext(Dispatchers.IO) {
            try { port?.close() } catch (_: Exception) {}
            port = null
            usbHelper.unregister()
        }
    }

    private fun indexOf(arr: ByteArray, len: Int, term: ByteArray): Int {
        outer@ for (i in 0 until len - term.size + 1) {
            for (j in term.indices) if (arr[i + j] != term[j]) continue@outer
            return i
        }
        return -1
    }
}
