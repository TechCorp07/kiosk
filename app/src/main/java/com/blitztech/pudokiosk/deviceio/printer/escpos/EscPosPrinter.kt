package com.blitztech.pudokiosk.deviceio.printer.escpos

import android.content.Context
import android.graphics.Bitmap
import android.hardware.usb.*
import com.blitztech.pudokiosk.usb.UsbHelper
import java.nio.ByteBuffer

class EscPosPrinter(private val ctx: Context, private val usbHelper: UsbHelper) {
    private var conn: UsbDeviceConnection? = null
    private var epOut: UsbEndpoint? = null

    fun connect(): Boolean {
        val usb = usbHelper.manager()
        // Try to find any printer-like device (Class 7) or just take first with bulk OUT endpoint
        val device = usb.deviceList.values.firstOrNull { d ->
            d.interfaceCount > 0 && (0 until d.interfaceCount).any { i ->
                val intf = d.getInterface(i)
                (intf.interfaceClass == UsbConstants.USB_CLASS_PRINTER) ||
                        (0 until intf.endpointCount).any { e -> intf.getEndpoint(e).type == UsbConstants.USB_ENDPOINT_XFER_BULK && intf.getEndpoint(e).direction == UsbConstants.USB_DIR_OUT }
            }
        } ?: return false

        if (!usbHelper.hasPermission(device)) return false

        val intf = device.getInterface(0)
        val outEp = (0 until intf.endpointCount).map { intf.getEndpoint(it) }
            .firstOrNull { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT } ?: return false

        val connection = usb.openDevice(device) ?: return false
        if (!connection.claimInterface(intf, true)) { connection.close(); return false }

        conn = connection
        epOut = outEp
        // Initialize ESC/POS
        write(byteArrayOf(0x1B, 0x40)) // ESC @
        return true
    }

    fun disconnect() {
        try { conn?.close() } catch (_: Exception) {}
        conn = null; epOut = null
    }

    fun write(bytes: ByteArray) {
        val c = conn ?: return
        val ep = epOut ?: return
        c.bulkTransfer(ep, bytes, bytes.size, 1000)
    }

    fun text(line: String) {
        write(line.toByteArray(Charsets.UTF_8))
        write(byteArrayOf(0x0A)) // LF
    }

    fun center(on: Boolean = true) {
        // ESC a n (0:left 1:center 2:right)
        write(byteArrayOf(0x1B, 0x61, if (on) 0x01 else 0x00))
    }

    fun cutPartial() {
        // GS V 1 (partial cut; some printers use 0x56)
        write(byteArrayOf(0x1D, 0x56, 0x01))
    }

    // Send image as ESC * (raster bit image)
    fun printBitmap(bmp: Bitmap) {
        val bw = toMono(bmp)
        val bytes = rasterize(bw)
        write(bytes)
        write(byteArrayOf(0x0A))
    }

    private fun toMono(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val threshold = 127
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = src.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val gray = (0.3*r + 0.59*g + 0.11*b).toInt()
                val v = if (gray < threshold) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                out.setPixel(x, y, v)
            }
        }
        return out
    }

    private fun rasterize(bmp: Bitmap): ByteArray {
        // ESC * m nL nH [data] for bit image (m=33 for 24-dot double density)
        val w = bmp.width; val h = bmp.height
        val bytesPerRow = (w + 7) / 8
        val buffer = ByteBuffer.allocate( (bytesPerRow + 5) * h )
        for (y in 0 until h) {
            val nL = (bytesPerRow and 0xFF).toByte()
            val nH = ((bytesPerRow shr 8) and 0xFF).toByte()
            buffer.put(0x1B.toByte()); buffer.put(0x2A) // ESC *
            buffer.put(33) // m = 33
            buffer.put(nL); buffer.put(nH)
            var bit = 0; var byteVal = 0
            for (x in 0 until w) {
                val black = (bmp.getPixel(x, y) and 0xFF000000.toInt()) != 0
                byteVal = (byteVal shl 1) or if (black) 1 else 0
                bit++
                if (bit == 8) { buffer.put(byteVal.toByte()); bit = 0; byteVal = 0 }
            }
            if (bit != 0) { // pad last byte
                byteVal = byteVal shl (8 - bit)
                buffer.put(byteVal.toByte())
            }
            buffer.put(0x0A) // LF
        }
        return buffer.array().copyOf(buffer.position())
    }
}
