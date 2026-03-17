package com.blitztech.pudokiosk.deviceio

import android.content.Context
import android.util.Log
import com.blitztech.pudokiosk.deviceio.camera.SecurityCameraManager
import com.blitztech.pudokiosk.deviceio.printer.CustomTG2480HIIIDriver
import com.blitztech.pudokiosk.deviceio.rs232.BarcodeScanner
import com.blitztech.pudokiosk.deviceio.rs485.LockerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Singleton manager for all hardware devices
 * Ensures proper initialization and thread-safe access
 */
class HardwareManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "HardwareManager"

        @Volatile
        private var instance: HardwareManager? = null

        fun getInstance(context: Context): HardwareManager {
            return instance ?: synchronized(this) {
                instance ?: HardwareManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // Hardware components
    private var _printerDriver: CustomTG2480HIIIDriver? = null
    private var _lockerController: LockerController? = null
    private var _barcodeScanner: Any? = null // BarcodeScanner is an object

    /** Speaker — always available (falls back to ToneGenerator). */
    val speaker: SpeakerManager by lazy { SpeakerManager.getInstance(context) }

    /** USB webcam security camera — non-blocking, always safe to call. */
    val camera: SecurityCameraManager by lazy { SecurityCameraManager.getInstance(context) }

    /** Alias used by Activity/Fragment code. */
    val speakerManager: SpeakerManager get() = speaker

    /**
     * Factory method: creates a [DoorMonitor] for the given lock.
     * Callers then call [DoorMonitor.start] with the desired callbacks.
     */
    fun createDoorMonitor(
        lockerController: com.blitztech.pudokiosk.deviceio.rs485.LockerController,
        lockNumber: Int,
        coroutineScope: kotlinx.coroutines.CoroutineScope
    ): DoorMonitor {
        return DoorMonitor(
            lockerController = lockerController,
            lockNumber = lockNumber,
            scope = coroutineScope
        )
    }

    // Initialization states
    private var printerInitialized = false
    private var lockerInitialized = false
    private var scannerInitialized = false

    // Mutex for thread-safe operations
    private val printerMutex = Mutex()
    private val lockerMutex = Mutex()

    /**
     * Get printer driver instance
     */
    suspend fun getPrinter(): CustomTG2480HIIIDriver? = printerMutex.withLock {
        if (_printerDriver == null) {
            try {
                _printerDriver = CustomTG2480HIIIDriver(context, enableAutoReconnect = true)
                // Printer auto-connects on initialization
                printerInitialized = true
                Log.d(TAG, "Printer initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Printer initialization error", e)
                _printerDriver = null
                printerInitialized = false
            }
        }
        _printerDriver
    }

    /**
     * Get locker controller instance
     */
    suspend fun getLocker(): LockerController? = lockerMutex.withLock {
        if (_lockerController == null) {
            try {
                _lockerController = LockerController(context)
                val connected = _lockerController?.connect() ?: false
                lockerInitialized = connected

                if (connected) {
                    Log.d(TAG, "Locker controller initialized successfully")
                } else {
                    Log.w(TAG, "Locker controller connection failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Locker controller initialization error", e)
                _lockerController = null
                lockerInitialized = false
            }
        }
        _lockerController
    }

    /**
     * Get barcode scanner instance
     */
    fun getScanner(): Any? {
        if (!scannerInitialized) {
            try {
                _barcodeScanner = BarcodeScanner
                scannerInitialized = true
                Log.d(TAG, "Barcode scanner initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Barcode scanner initialization error", e)
                _barcodeScanner = null
                scannerInitialized = false
            }
        }
        return _barcodeScanner
    }

    /**
     * Check if printer is initialized and connected
     */
    fun isPrinterReady(): Boolean = printerInitialized

    /**
     * Check if locker is initialized and connected
     */
    fun isLockerReady(): Boolean = lockerInitialized

    /**
     * Check if scanner is initialized
     */
    fun isScannerReady(): Boolean = scannerInitialized

    /**
     * Check if camera is available
     */
    fun isCameraReady(): Boolean = camera.isCameraAvailable()

    /**
     * Get hardware status summary
     */
    fun getHardwareStatus(): HardwareStatus {
        return HardwareStatus(
            printerReady = printerInitialized,
            lockerReady = lockerInitialized,
            scannerReady = scannerInitialized,
            cameraReady = camera.isCameraAvailable()
        )
    }

    /**
     * Reinitialize printer
     */
    suspend fun reinitializePrinter(): Boolean = printerMutex.withLock {
        try {
            // Release current instance
            _printerDriver = null
            printerInitialized = false

            return@withLock getPrinter() != null
        } catch (e: Exception) {
            Log.e(TAG, "Printer reinitialization error", e)
            false
        }
    }

    /**
     * Reinitialize locker
     */
    suspend fun reinitializeLocker(): Boolean = lockerMutex.withLock {
        try {
            _lockerController?.disconnect()
            _lockerController = null
            lockerInitialized = false

            return@withLock getLocker() != null
        } catch (e: Exception) {
            Log.e(TAG, "Locker reinitialization error", e)
            false
        }
    }

    /**
     * Disconnect all hardware
     */
    suspend fun disconnectAll() {
        printerMutex.withLock {
            try {
                // Printer doesn't need explicit disconnect
                _printerDriver = null
                printerInitialized = false
                Log.d(TAG, "Printer released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing printer", e)
            }
        }

        lockerMutex.withLock {
            try {
                _lockerController?.disconnect()
                _lockerController = null
                lockerInitialized = false
                Log.d(TAG, "Locker controller disconnected")
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting locker", e)
            }
        }
    }

    /**
     * Hardware status data class
     */
    data class HardwareStatus(
        val printerReady: Boolean,
        val lockerReady: Boolean,
        val scannerReady: Boolean,
        val cameraReady: Boolean = false
    ) {
        val allReady: Boolean get() = printerReady && lockerReady && scannerReady
        val anyReady: Boolean get() = printerReady || lockerReady || scannerReady

        fun toReadableString(): String {
            return buildString {
                appendLine("Hardware Status:")
                appendLine("  Printer: ${if (printerReady) "✓ Ready" else "✗ Not Ready"}")
                appendLine("  Locker: ${if (lockerReady) "✓ Ready" else "✗ Not Ready"}")
                appendLine("  Scanner: ${if (scannerReady) "✓ Ready" else "✗ Not Ready"}")
                appendLine("  Camera: ${if (cameraReady) "✓ Ready" else "✗ Not Ready (optional)"}")
            }
        }
    }
}

/**
 * Extension function for easy hardware access in Activities/Fragments
 */
fun Context.getHardwareManager(): HardwareManager {
    return HardwareManager.getInstance(this)
}