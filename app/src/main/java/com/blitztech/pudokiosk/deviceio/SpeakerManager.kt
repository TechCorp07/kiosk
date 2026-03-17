package com.blitztech.pudokiosk.deviceio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.media.AudioManager
import android.util.Log
import com.blitztech.pudokiosk.R
import kotlinx.coroutines.*

/**
 * SpeakerManager — centralised audio alert controller for the kiosk.
 *
 * Plays three categories of sound:
 *  - **Door close reminder**: looping prompt while a locker door is open
 *  - **Success chime**: single play on successful operation
 *  - **Error alert**: single play on failure
 *
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

    // -------------------------------------------------------------------------
    //  Public API
    // -------------------------------------------------------------------------

    /**
     * Starts looping the "close the door" reminder every [REMINDER_REPEAT_INTERVAL_MS].
     * Call [stopDoorCloseReminder] when the door is confirmed closed.
     */
    fun startDoorCloseReminder() {
        if (reminderJob?.isActive == true) return
        Log.d(TAG, "🔊 Starting door-close reminder loop")
        reminderJob = scope.launch {
            while (isActive) {
                playDoorCloseReminder()
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
    fun playSuccessChime() = playRawOrBeep(R.raw.success_chime, ToneGenerator.TONE_PROP_ACK)

    /** Plays a single error alert. */
    fun playErrorAlert() = playRawOrBeep(R.raw.error_alert, ToneGenerator.TONE_PROP_NACK)

    /** Releases all resources. Call when the app is shutting down. */
    fun release() {
        stopDoorCloseReminder()
        scope.cancel()
        instance = null
    }

    // -------------------------------------------------------------------------
    //  Internal helpers
    // -------------------------------------------------------------------------

    private fun playDoorCloseReminder() {
        playRawOrBeep(R.raw.close_door_reminder, ToneGenerator.TONE_PROP_BEEP2)
    }

    /**
     * Attempts to play a bundled raw audio resource.
     * If the resource does not exist (0-byte placeholder), falls back to [ToneGenerator].
     */
    private fun playRawOrBeep(rawResId: Int, toneType: Int) {
        try {
            val player = MediaPlayer().apply {
                val afd = context.resources.openRawResourceFd(rawResId)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
                setOnCompletionListener { release() }
                setOnErrorListener { mp, _, _ -> mp.release(); true }
            }
            player.start()
        } catch (e: Exception) {
            // Raw file not yet added — fall back to system tone
            Log.d(TAG, "Raw audio unavailable (${e.message}), using ToneGenerator fallback")
            try {
                val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                tg.startTone(toneType, 800)
                // ToneGenerator auto-stops; release after tone duration
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
