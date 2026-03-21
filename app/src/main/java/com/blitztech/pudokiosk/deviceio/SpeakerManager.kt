package com.blitztech.pudokiosk.deviceio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.media.ToneGenerator
import android.media.AudioManager
import android.util.Log
import com.blitztech.pudokiosk.R
import kotlinx.coroutines.*

/**
 * SpeakerManager — centralised audio alert controller for the kiosk.
 *
 * Uses [SoundPool] for efficient, low-latency audio playback.
 * Pre-loads sounds once, plays from pool with zero per-call allocation.
 * Falls back gracefully to [ToneGenerator] beeps when raw audio files
 * are not yet bundled with the APK.
 */
class SpeakerManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SpeakerManager"

        /** Delay between repeated door-close reminders (ms). */
        private const val REMINDER_REPEAT_INTERVAL_MS = 5_000L

        @Volatile
        private var instance: SpeakerManager? = null

        fun getInstance(context: Context): SpeakerManager {
            return instance ?: synchronized(this) {
                instance ?: SpeakerManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var reminderJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── SoundPool — pre-loaded, reusable audio ──────────────────────────
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val soundPool: SoundPool by lazy {
        SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()
    }

    // Sound IDs (0 = not loaded / load failed)
    private val doorReminderId: Int by lazy { loadSound(R.raw.close_door_reminder) }
    private val successChimeId: Int by lazy { loadSound(R.raw.success_chime) }
    private val errorAlertId: Int by lazy { loadSound(R.raw.error_alert) }

    private fun loadSound(rawResId: Int): Int {
        return try {
            soundPool.load(context, rawResId, 1)
        } catch (e: Exception) {
            Log.d(TAG, "Could not load sound resource: ${e.message}")
            0
        }
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Starts looping the "close the door" reminder every [REMINDER_REPEAT_INTERVAL_MS].
     * Call [stopDoorCloseReminder] when the door is confirmed closed.
     */
    fun startDoorCloseReminder() {
        if (reminderJob?.isActive == true) return
        Log.d(TAG, "🔊 Starting door-close reminder loop")
        reminderJob = scope.launch {
            while (isActive) {
                playSound(doorReminderId, ToneGenerator.TONE_PROP_BEEP2)
                delay(REMINDER_REPEAT_INTERVAL_MS)
            }
        }
    }

    /** Stops the looping door-close reminder. */
    fun stopDoorCloseReminder() {
        reminderJob?.cancel()
        reminderJob = null
        Log.d(TAG, "🔇 Door-close reminder stopped")
    }

    /** Plays a single success chime. */
    fun playSuccessChime() = playSound(successChimeId, ToneGenerator.TONE_PROP_ACK)

    /** Plays a single error alert. */
    fun playErrorAlert() = playSound(errorAlertId, ToneGenerator.TONE_PROP_NACK)

    /** Releases all resources. Call when the app is shutting down. */
    fun release() {
        stopDoorCloseReminder()
        scope.cancel()
        try { soundPool.release() } catch (_: Exception) {}
        instance = null
    }

    // ── Internal ────────────────────────────────────────────────────────

    private fun playSound(soundId: Int, fallbackTone: Int) {
        if (soundId > 0) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            // Raw file not loaded — fall back to system tone
            try {
                val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                tg.startTone(fallbackTone, 800)
                scope.launch {
                    delay(900)
                    tg.release()
                }
            } catch (te: Exception) {
                Log.w(TAG, "ToneGenerator also failed: ${te.message}")
            }
        }
    }
}

