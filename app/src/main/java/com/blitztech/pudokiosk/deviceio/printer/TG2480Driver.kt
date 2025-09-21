package com.blitztech.pudokiosk.deviceio.printer

import android.content.Context
import android.hardware.usb.UsbDevice
import android.widget.Toast
import com.blitztech.pudokiosk.deviceio.printer.escpos.EscPosPrinter
import com.blitztech.pudokiosk.deviceio.printer.escpos.QrBitmap
import com.blitztech.pudokiosk.usb.UsbHelper

data class CustomerLabel(val tracking: String, val size: String)

class TG2480Driver(private val ctx: Context) {
    private val usbHelper = UsbHelper(ctx)
    private val printer = EscPosPrinter(ctx, usbHelper)

    fun printCustomerLabel(label: CustomerLabel) {
        usbHelper.register()
        try {
            val dev = usbHelper.devices().firstOrNull()
            if (dev == null) {
                Toast.makeText(ctx, "No USB device found", Toast.LENGTH_SHORT).show()
                return
            }
            if (!usbHelper.hasPermission(dev)) {
                usbHelper.requestPermission(dev) { d, granted ->
                    if (granted) actuallyPrint(label) else
                        Toast.makeText(ctx, "USB permission denied", Toast.LENGTH_SHORT).show()
                }
            } else {
                actuallyPrint(label)
            }
        } finally {
            // no-op, we keep helper registered for callbacks; activity teardown should call unregister in onDestroy if needed
        }
    }

    private fun actuallyPrint(label: CustomerLabel) {
        if (!printer.connect()) {
            Toast.makeText(ctx, "Printer connect failed", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            printer.center(true)
            printer.text("PUDO Label")
            printer.text("Tracking: ${label.tracking}")
            printer.text("Size: ${label.size}")
            val qr = QrBitmap.generate(label.tracking, 256)
            printer.printBitmap(qr)
            printer.text("----------------------")
            printer.cutPartial()
            Toast.makeText(ctx, "Printed label ${label.tracking}", Toast.LENGTH_SHORT).show()
        } finally {
            printer.disconnect()
        }
    }
}
