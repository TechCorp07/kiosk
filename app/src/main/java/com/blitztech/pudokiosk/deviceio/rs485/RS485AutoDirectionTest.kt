package com.blitztech.pudokiosk.deviceio.rs485

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Test if RS485 converter has automatic direction control
 * (doesn't need RTS/DTR at all)
 */
class RS485AutoDirectionTest(private val ctx: Context) {

    companion object {
        private const val TAG = "RS485AutoTest"
    }

    suspend fun testAutoDirection(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "=== TESTING AUTO-DIRECTION RS485 CONVERTER ===")
        Log.i(TAG, "This test does NOT touch RTS or DTR at all")

        try {
            val usbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
            val targetDevice = usbManager.deviceList.values.firstOrNull {
                it.vendorId == 0x04E2 && it.productId == 0x1414
            } ?: run {
                Log.e(TAG, "❌ Device not found")
                return@withContext false
            }

            val connection = usbManager.openDevice(targetDevice) ?: run {
                Log.e(TAG, "❌ Permission denied")
                return@withContext false
            }

            val driver = UsbSerialProber.getDefaultProber().probeDevice(targetDevice)
            val port = driver?.ports?.getOrNull(2) ?: driver?.ports?.first()

            if (port == null) {
                connection.close()
                Log.e(TAG, "❌ No port found")
                return@withContext false
            }

            port.open(connection)
            port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            // Leave RTS and DTR in their default states - DON'T TOUCH THEM
            Log.i(TAG, "NOT touching RTS or DTR - leaving at defaults")
            delay(100)

            // Clear any old data
            try {
                val clearBuf = ByteArray(256)
                var attempts = 0
                while (attempts < 5) {
                    val read = port.read(clearBuf, 50)
                    if (read <= 0) break
                    attempts++
                }
            } catch (e: Exception) {
                // Ignore
            }

            Log.i(TAG, "Sending command...")
            val command = byteArrayOf(
                0x90.toByte(), 0x06.toByte(), 0x12.toByte(),
                0x00.toByte(), 0x01.toByte(), 0x03.toByte()
            )

            port.write(command, 1000)
            Log.i(TAG, "Command sent, waiting for response...")

            val startTime = System.currentTimeMillis()
            val responseData = mutableListOf<Byte>()

            while (System.currentTimeMillis() - startTime < 3000) {
                val buffer = ByteArray(256)
                val bytesRead = port.read(buffer, 100)

                if (bytesRead > 0) {
                    responseData.addAll(buffer.take(bytesRead))
                    Log.d(TAG, "Received ${bytesRead} bytes: ${toHex(buffer.take(bytesRead).toByteArray())}")

                    // Check for complete frame
                    if (responseData.size >= 7) {
                        val frame = responseData.toByteArray()
                        for (i in 0..frame.size - 7) {
                            if (frame[i] == 0x90.toByte() &&
                                frame[i + 6] == 0x03.toByte() &&
                                frame[i + 1] == 0x07.toByte()) {

                                val completeFrame = frame.sliceArray(i until i + 7)
                                Log.i(TAG, "")
                                Log.i(TAG, "✅ ✅ ✅ SUCCESS! ✅ ✅ ✅")
                                Log.i(TAG, "Complete response: ${toHex(completeFrame)}")
                                Log.i(TAG, "")
                                Log.i(TAG, "RESULT: Your RS485 converter has AUTOMATIC direction control!")
                                Log.i(TAG, "It switches TX/RX automatically based on data flow.")
                                Log.i(TAG, "You DON'T need to control RTS or DTR at all.")
                                Log.i(TAG, "")
                                Log.i(TAG, "Lock status: ${if (completeFrame[4] == 0x01.toByte()) "OPEN" else "CLOSED"}")

                                port.close()
                                connection.close()
                                return@withContext true
                            }
                        }
                    }
                }

                delay(10)
            }

            if (responseData.isNotEmpty()) {
                Log.w(TAG, "❌ Received partial data: ${toHex(responseData.toByteArray())}")
            } else {
                Log.e(TAG, "❌ No response received")
            }

            Log.e(TAG, "")
            Log.e(TAG, "FAILED - Check:")
            Log.e(TAG, "1. Is STM32 powered? (heartbeat LED blinking?)")
            Log.e(TAG, "2. Are A/B lines connected correctly?")
            Log.e(TAG, "3. Is there a switch/jumper on the RS485 converter?")

            port.close()
            connection.close()
            return@withContext false

        } catch (e: Exception) {
            Log.e(TAG, "Test error: ${e.message}", e)
            return@withContext false
        }
    }

    private fun toHex(data: ByteArray): String {
        return data.joinToString(" ") { "%02X".format(it) }
    }
}