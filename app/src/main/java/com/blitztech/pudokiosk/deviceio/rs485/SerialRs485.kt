package com.blitztech.pudokiosk.deviceio.rs485

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * RS485 Serial Communication Handler
 * Configured for STM32L412 locker control boards via MAX485 IC
 * Default baud rate: 9600, 8N1 (8 data bits, no parity, 1 stop bit)
 */
class SerialRs485(private val ctx: Context) {
    private var port: UsbSerialPort? = null
    private var isConnected = false

    companion object {
        private const val DEFAULT_BAUD_RATE = 9600
        private const val DEFAULT_TIMEOUT_MS = 500
        private const val INTER_FRAME_DELAY_MS = 50L // Delay between frames for RS485
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    /**
     * Open RS485 connection with specified parameters
     */
    suspend fun open(
        baud: Int = DEFAULT_BAUD_RATE,
        dataBits: Int = 8,
        stopBits: Int = UsbSerialPort.STOPBITS_1,
        parity: Int = UsbSerialPort.PARITY_NONE
    ) = withContext(Dispatchers.IO) {
        if (isConnected && port != null) return@withContext

        val usb = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usb)
        require(availableDrivers.isNotEmpty()) { "No USB serial drivers found" }

        // Find the correct RS485 adapter (you may need to filter by VID/PID)
        val driver: UsbSerialDriver = availableDrivers.firstOrNull { driver ->
            // Add specific filtering here if needed for your RS485-USB adapter
            true // For now, use the first available driver
        } ?: throw IllegalStateException("No compatible RS485 adapter found")

        val connection = usb.openDevice(driver.device)
            ?: throw IllegalStateException("No permission to open USB device: ${driver.device.deviceName}")

        port = driver.ports.firstOrNull()?.apply {
            open(connection)
            setParameters(baud, dataBits, stopBits, parity)

            // Configure for RS485 (important for proper communication)
            dtr = true
            rts = true

            // Flush any existing data
            purgeHwBuffers(true, true)
        } ?: throw IllegalStateException("No serial ports available on device")

        isConnected = true

        // Small delay to ensure port is ready
        delay(100)
    }

    /**
     * Write command and read response with proper RS485 timing
     */
    suspend fun writeRead(
        command: ByteArray,
        expectedResponseSize: Int,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
        retries: Int = MAX_RETRY_ATTEMPTS
    ): ByteArray = withContext(Dispatchers.IO) {
        val p = port ?: throw IllegalStateException("Serial port not open")

        repeat(retries) { attempt ->
            try {
                // Clear any pending data
                p.purgeHwBuffers(true, true)
                delay(10)

                // Send command
                p.write(command, timeoutMs)
                if (command.isEmpty()) {
                    throw IllegalStateException("Failed to write complete command. Command size is zero.")
                }

                // RS485 inter-frame delay
                delay(INTER_FRAME_DELAY_MS)

                // Read response
                val buffer = ByteArray(expectedResponseSize)
                val bytesRead = p.read(buffer, timeoutMs)

                if (bytesRead > 0) {
                    return@withContext buffer.copyOf(bytesRead)
                } else if (attempt == retries - 1) {
                    throw IllegalStateException("No response received after $retries attempts")
                }

            } catch (e: Exception) {
                if (attempt == retries - 1) throw e
                // Wait before retry
                delay(100)
            }
        }

        throw IllegalStateException("Failed to communicate after $retries attempts")
    }

    /**
     * Write data only (no response expected)
     */
    suspend fun write(data: ByteArray, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Int =
        withContext(Dispatchers.IO) {
            val p = port ?: throw IllegalStateException("Serial port not open")
            p.write(data, timeoutMs)
            data.size
        }

    /**
     * Read data only
     */
    suspend fun read(buffer: ByteArray, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Int =
        withContext(Dispatchers.IO) {
            val p = port ?: throw IllegalStateException("Serial port not open")
            p.read(buffer, timeoutMs)
        }

    /**
     * Check if port is connected and ready
     */
    fun isReady(): Boolean = isConnected && port != null

    /**
     * Get available USB serial devices for debugging
     */
    fun getAvailableDevices(): List<String> {
        val usb = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usb)
        return drivers.map { "${it.device.deviceName} - ${it.device.vendorId}:${it.device.productId}" }
    }

    /**
     * Close the serial connection
     */
    suspend fun close() = withContext(Dispatchers.IO) {
        try {
            port?.close()
        } catch (e: Exception) {
            // Log but don't throw - we want to ensure cleanup happens
        } finally {
            port = null
            isConnected = false
        }
    }
}