package com.blitztech.pudokiosk.deviceio.rs485

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Diagnostic Tool: Auto-detect RS485 Direction Control Pin
 *
 * Tests both RTS and DTR to determine which controls your RS485 adapter
 */
class RS485DirectionTester(private val ctx: Context) {

    companion object {
        private const val TAG = "RS485DirectionTest"
        private const val BAUD_RATE = 9600
    }

    data class TestResult(
        val controlPin: String,
        val success: Boolean,
        val responseReceived: Boolean,
        val responseData: ByteArray? = null,
        val errorMessage: String? = null
    )

    /**
     * Test both RTS and DTR to see which one works
     * Returns the working control pin or null if neither works
     */
    suspend fun autoDetectDirectionPin(): String? = withContext(Dispatchers.IO) {
        Log.i(TAG, "=== AUTO-DETECTING RS485 DIRECTION PIN ===")

        // Test 1: Try RTS (most common)
        Log.i(TAG, "\n🧪 TEST 1: Testing with RTS control...")
        val rtsResult = testWithControlPin(useRTS = true)

        if (rtsResult.success && rtsResult.responseReceived) {
            Log.i(TAG, "✅ RTS WORKS! Response received: ${rtsResult.responseData?.let { toHex(it) }}")
            return@withContext "RTS"
        } else {
            Log.i(TAG, "❌ RTS failed: ${rtsResult.errorMessage ?: "No response"}")
        }

        delay(1000) // Wait between tests

        // Test 2: Try DTR
        Log.i(TAG, "\n🧪 TEST 2: Testing with DTR control...")
        val dtrResult = testWithControlPin(useRTS = false)

        if (dtrResult.success && dtrResult.responseReceived) {
            Log.i(TAG, "✅ DTR WORKS! Response received: ${dtrResult.responseData?.let { toHex(it) }}")
            return@withContext "DTR"
        } else {
            Log.i(TAG, "❌ DTR failed: ${dtrResult.errorMessage ?: "No response"}")
        }

        // Test 3: Try both RTS and DTR HIGH (some adapters need both)
        Log.i(TAG, "\n🧪 TEST 3: Testing with BOTH RTS+DTR...")
        val bothResult = testWithControlPin(useRTS = true, useDTR = true)

        if (bothResult.success && bothResult.responseReceived) {
            Log.i(TAG, "✅ RTS+DTR WORKS! Your adapter needs both pins")
            return@withContext "RTS+DTR"
        } else {
            Log.i(TAG, "❌ RTS+DTR failed: ${bothResult.errorMessage ?: "No response"}")
        }

        Log.e(TAG, "\n❌ NONE OF THE CONTROL PINS WORKED")
        Log.e(TAG, "Possible issues:")
        Log.e(TAG, "1. STM32 not powered or not running")
        Log.e(TAG, "2. RS485 wiring issue (A/B swapped or not connected)")
        Log.e(TAG, "3. Wrong baud rate or station number")
        Log.e(TAG, "4. RS485 adapter doesn't use standard control pins")

        return@withContext null
    }

    /**
     * Test communication with a specific control pin configuration
     */
    private suspend fun testWithControlPin(
        useRTS: Boolean,
        useDTR: Boolean = false
    ): TestResult = withContext(Dispatchers.IO) {

        var port: UsbSerialPort? = null

        try {
            // Connect to device
            val usbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
            val deviceList = usbManager.deviceList
            var targetDevice: UsbDevice? = null

            // Find RS485 device (VID:04E2 PID:1414)
            for (device in deviceList.values) {
                if (device.vendorId == 0x04E2 && device.productId == 0x1414) {
                    targetDevice = device
                    break
                }
            }

            if (targetDevice == null) {
                return@withContext TestResult(
                    controlPin = if (useRTS) "RTS" else "DTR",
                    success = false,
                    responseReceived = false,
                    errorMessage = "Device not found"
                )
            }

            val connection = usbManager.openDevice(targetDevice)
            if (connection == null) {
                return@withContext TestResult(
                    controlPin = if (useRTS) "RTS" else "DTR",
                    success = false,
                    responseReceived = false,
                    errorMessage = "Permission denied"
                )
            }

            val driver = UsbSerialProber.getDefaultProber().probeDevice(targetDevice)
            if (driver == null) {
                connection.close()
                return@withContext TestResult(
                    controlPin = if (useRTS) "RTS" else "DTR",
                    success = false,
                    responseReceived = false,
                    errorMessage = "No driver found"
                )
            }

            val selectedPort = driver.ports.getOrNull(2) ?: driver.ports.first()
            selectedPort.open(connection)
            selectedPort.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            // Set initial state: RX mode (control pins LOW)
            selectedPort.rts = false
            selectedPort.dtr = false
            delay(50)

            port = selectedPort

            // Clear any pending data
            try {
                val clearBuffer = ByteArray(256)
                var attempts = 0
                while (attempts < 5) {
                    val read = selectedPort.read(clearBuffer, 50)
                    if (read <= 0) break
                    attempts++
                }
            } catch (e: Exception) {
                // Ignore
            }

            Log.d(TAG, "Sending test command...")

            // Status check command for lock 1, station 0
            val testCommand = byteArrayOf(
                0x90.toByte(), 0x06.toByte(), 0x12.toByte(),
                0x00.toByte(), 0x01.toByte(), 0x03.toByte()
            )

            // === TRANSMIT MODE ===
            if (useRTS) selectedPort.rts = true
            if (useDTR) selectedPort.dtr = true
            delay(2) // Wait for transceiver switch

            // Send command
            selectedPort.write(testCommand, 1000)

            // Wait for transmission to complete
            val txTime = ((testCommand.size * 10 * 1000) / BAUD_RATE).toLong() + 5
            delay(txTime)

            // === RECEIVE MODE ===
            selectedPort.rts = false
            selectedPort.dtr = false
            delay(2) // Wait for transceiver switch

            Log.d(TAG, "Waiting for response...")

            // Wait for response (with timeout)
            val startTime = System.currentTimeMillis()
            val responseData = mutableListOf<Byte>()

            while (System.currentTimeMillis() - startTime < 2000) {
                val buffer = ByteArray(256)
                val bytesRead = selectedPort.read(buffer, 100)

                if (bytesRead > 0) {
                    responseData.addAll(buffer.take(bytesRead))

                    // Check for complete frame
                    if (responseData.size >= 7) {
                        val frame = responseData.toByteArray()
                        for (i in 0..frame.size - 7) {
                            if (frame[i] == 0x90.toByte() &&
                                frame[i + 6] == 0x03.toByte() &&
                                frame[i + 1] == 0x07.toByte()) {

                                val completeFrame = frame.sliceArray(i until i + 7)

                                selectedPort.close()
                                connection.close()

                                return@withContext TestResult(
                                    controlPin = when {
                                        useRTS && useDTR -> "RTS+DTR"
                                        useRTS -> "RTS"
                                        else -> "DTR"
                                    },
                                    success = true,
                                    responseReceived = true,
                                    responseData = completeFrame
                                )
                            }
                        }
                    }
                }

                delay(10)
            }

            // No response received
            selectedPort.close()
            connection.close()

            return@withContext TestResult(
                controlPin = if (useRTS) "RTS" else "DTR",
                success = true,
                responseReceived = false,
                errorMessage = "Command sent but no response"
            )

        } catch (e: Exception) {
            Log.e(TAG, "Test error: ${e.message}", e)

            try {
                port?.close()
            } catch (ex: Exception) {
                // Ignore
            }

            return@withContext TestResult(
                controlPin = if (useRTS) "RTS" else "DTR",
                success = false,
                responseReceived = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }

    private fun toHex(data: ByteArray): String {
        return data.joinToString(" ") { "%02X".format(it) }
    }
}