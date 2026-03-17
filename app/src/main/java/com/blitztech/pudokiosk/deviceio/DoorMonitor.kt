package com.blitztech.pudokiosk.deviceio

import android.util.Log
import com.blitztech.pudokiosk.deviceio.rs485.LockerController
import com.blitztech.pudokiosk.deviceio.rs485.WinnsenProtocol
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DoorMonitor — polls a single locker compartment's door sensor
 * and reports state transitions via a [StateFlow].
 *
 * Usage:
 *   val monitor = DoorMonitor(lockerController, lockNumber = 5, scope = lifecycleScope)
 *   monitor.start(
 *     onDoorOpenTooLong = { speakerManager.playDoorCloseReminder() },
 *     onDoorTimeout     = { showTimeoutWarning() }
 *   )
 *   // ... user closes door ...
 *   // observe monitor.doorState; CLOSED → call monitor.stop()
 */
class DoorMonitor(
    private val lockerController: LockerController,
    private val lockNumber: Int,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "DoorMonitor"

        /** How often (ms) we poll the door sensor. */
        const val POLL_INTERVAL_MS = 1_000L

        /** Door has been open this long → play "close door" audio. */
        const val DOOR_ALERT_DELAY_MS = 10_000L

        /** Door has been open this long without being closed → escalate. */
        const val DOOR_MAX_OPEN_MS = 60_000L
    }

    // -------------------------------------------------------------------------

    enum class DoorState {
        /** Initial state before first poll. */
        UNKNOWN,
        /** Door is physically open. */
        OPEN,
        /** Door is physically closed. */
        CLOSED,
        /** Door was open too long without user response. */
        TIMEOUT,
        /** Hardware / communication error. */
        ERROR
    }

    private val _doorState = MutableStateFlow(DoorState.UNKNOWN)
    val doorState: StateFlow<DoorState> = _doorState.asStateFlow()

    private var monitorJob: Job? = null
    private var openSinceMs: Long = 0L
    private var alertFired = false

    // -------------------------------------------------------------------------

    /**
     * Start polling the door sensor.
     *
     * @param onDoorOpenTooLong  Called once when door has been open ≥ [DOOR_ALERT_DELAY_MS].
     * @param onDoorTimeout      Called once when door has been open ≥ [DOOR_MAX_OPEN_MS].
     * @param onDoorClosed       Called when the door transitions to CLOSED.
     */
    fun start(
        onDoorOpenTooLong: (() -> Unit)? = null,
        onDoorTimeout: (() -> Unit)? = null,
        onDoorClosed: (() -> Unit)? = null
    ) {
        if (monitorJob?.isActive == true) return     // already running

        alertFired = false
        openSinceMs = 0L
        Log.d(TAG, "▶ Starting door monitor for lock $lockNumber")

        monitorJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val result = lockerController.checkLockStatus(lockNumber)

                    val newState = when {
                        !result.success -> DoorState.ERROR
                        result.status == WinnsenProtocol.LockStatus.OPEN -> DoorState.OPEN
                        result.status == WinnsenProtocol.LockStatus.CLOSED -> DoorState.CLOSED
                        else -> DoorState.UNKNOWN
                    }

                    val previous = _doorState.value
                    _doorState.value = newState

                    when (newState) {
                        DoorState.OPEN -> {
                            val now = System.currentTimeMillis()
                            if (openSinceMs == 0L) openSinceMs = now

                            val openDuration = now - openSinceMs

                            // First alert at 10 s
                            if (!alertFired && openDuration >= DOOR_ALERT_DELAY_MS) {
                                alertFired = true
                                Log.w(TAG, "⚠ Door $lockNumber open ${openDuration / 1000}s → reminding user")
                                withContext(Dispatchers.Main) { onDoorOpenTooLong?.invoke() }
                            }

                            // Timeout at 60 s
                            if (openDuration >= DOOR_MAX_OPEN_MS) {
                                Log.e(TAG, "🚨 Door $lockNumber timeout after ${openDuration / 1000}s")
                                _doorState.value = DoorState.TIMEOUT
                                withContext(Dispatchers.Main) { onDoorTimeout?.invoke() }
                                return@launch
                            }
                        }

                        DoorState.CLOSED -> {
                            if (previous == DoorState.OPEN || previous == DoorState.UNKNOWN) {
                                Log.d(TAG, "✅ Door $lockNumber closed")
                                withContext(Dispatchers.Main) { onDoorClosed?.invoke() }
                            }
                            // Reset tracking
                            openSinceMs = 0L
                            alertFired = false
                        }

                        DoorState.ERROR -> {
                            Log.w(TAG, "⚠ Error checking door $lockNumber: ${result.errorMessage}")
                        }

                        else -> { /* UNKNOWN / TIMEOUT handled above */ }
                    }

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error polling door $lockNumber", e)
                    _doorState.value = DoorState.ERROR
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Stop monitoring. Safe to call multiple times.
     */
    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        Log.d(TAG, "⏹ Door monitor stopped for lock $lockNumber")
    }

    /** Convenience: true when the door is confirmed closed. */
    fun isClosed(): Boolean = doorState.value == DoorState.CLOSED
}
