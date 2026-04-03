package com.blitztech.pudokiosk.deviceio

import android.content.Context
import android.util.Log
import com.blitztech.pudokiosk.ZimpudoApp
import com.blitztech.pudokiosk.deviceio.rs485.LockerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * HardwareWatchdog — monitors the RS485 locker hardware connection
 * and triggers reconnect on failure.
 *
 * Runs in its own CoroutineScope using [Dispatchers.IO].
 * Should be started once from [ZimpudoApp.onCreate] or the kiosk
 * foreground service, and stopped when the app is destroyed.
 *
 * Heartbeat cycle (every [pingIntervalMs]):
 *   1. Attempt to connect if not connected
 *   2. Send a status ping to the locker board (board byte 0xFF = broadcast status check)
 *   3. On failure: log + increment failure counter
 *   4. On [MAX_CONSECUTIVE_FAILURES] consecutive failures: force reconnect
 *
 * TODO [PHASE 7]: Expose watchdog health status as LiveData or StateFlow
 *   for the technician dashboard so admins can see real-time hardware health.
 */
class HardwareWatchdog(private val context: Context) {

    companion object {
        private const val TAG = "HardwareWatchdog"
        private const val PING_INTERVAL_MS = 30_000L   // Ping every 30 seconds
        private const val RECONNECT_DELAY_MS = 5_000L  // Wait 5s between reconnect attempts
        private const val MAX_CONSECUTIVE_FAILURES = 3  // Reconnect after 3 missed pings
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var watchdogJob: Job? = null
    private var consecutiveFailures = 0

    @Volatile var isHardwareHealthy: Boolean = false
        private set

    /** Start the watchdog loop. Safe to call multiple times — idempotent. */
    fun start() {
        if (watchdogJob?.isActive == true) {
            Log.d(TAG, "Watchdog already running — skipping duplicate start")
            return
        }
        Log.d(TAG, "▶ HardwareWatchdog starting (ping every ${PING_INTERVAL_MS / 1000}s)")
        watchdogJob = scope.launch {
            while (isActive) {
                pingHardware()
                delay(PING_INTERVAL_MS)
            }
        }
    }

    /** Stop the watchdog — call from onDestroy or service stop. */
    fun stop() {
        watchdogJob?.cancel()
        watchdogJob = null
        Log.d(TAG, "⏹ HardwareWatchdog stopped")
    }

    private suspend fun pingHardware() {
        try {
            val hw = HardwareManager.getInstance(context)
            val locker = hw.getLocker()

            if (locker == null) {
                Log.w(TAG, "No locker registered — hardware not initialized yet")
                isHardwareHealthy = false
                consecutiveFailures++
                handleFailureThreshold()
                return
            }

            if (!locker.isConnected) {
                Log.w(TAG, "Locker disconnected — attempting reconnect…")
                attemptReconnect(locker)
                return
            }

            // Ping: request a status check for lock 1 as heartbeat
            val pingResult = locker.checkLockStatus(lockNumber = 1)
            if (pingResult.success) {
                consecutiveFailures = 0
                isHardwareHealthy = true
                Log.v(TAG, "💓 Hardware ping OK (lock 1 status: ${pingResult.status.displayName})")
            } else {
                handlePingFailure("Board status returned failure: ${pingResult.errorMessage}")
            }

        } catch (e: Exception) {
            handlePingFailure("Exception during ping: ${e.message}")
        }
    }

    private suspend fun attemptReconnect(locker: LockerController) {
        try {
            Log.d(TAG, "🔌 Attempting reconnect to locker…")
            delay(RECONNECT_DELAY_MS)
            locker.connect()
            if (locker.isConnected) {
                consecutiveFailures = 0
                isHardwareHealthy = true
                Log.d(TAG, "✅ Locker reconnected successfully")
            } else {
                handlePingFailure("Reconnect attempted but connection still failed")
            }
        } catch (e: Exception) {
            handlePingFailure("Reconnect exception: ${e.message}")
        }
    }

    private fun handlePingFailure(reason: String) {
        consecutiveFailures++
        isHardwareHealthy = false
        Log.w(TAG, "⚠️ Ping failure #$consecutiveFailures — $reason")
        handleFailureThreshold()
    }

    private fun handleFailureThreshold() {
        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
            Log.e(TAG, "🆘 Hardware unresponsive after $consecutiveFailures attempts — scheduling force-reconnect on next ping")
            // Reset counter so next ping gets a fresh reconnect attempt
            consecutiveFailures = 0
            // Optionally: broadcast an intent for the UI to show a hardware error banner
            try {
                val intent = android.content.Intent("com.blitztech.pudokiosk.HARDWARE_ERROR")
                    .setPackage(context.packageName)
                context.sendBroadcast(intent)
            } catch (_: Exception) { /* best-effort */ }
        }
    }
}
