package com.blitztech.pudokiosk.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log

class PromptPlayer(private val ctx: Context) {
    private var player: MediaPlayer? = null

    fun play(resId: Int) {
        stop()

        // MediaPlayer.create may return null if the resource is missing/unsupported.
        val mp = MediaPlayer.create(ctx, resId)
        if (mp == null) {
            Log.e("PromptPlayer", "MediaPlayer.create returned null for resId=$resId (missing file or unsupported codec).")
            return
        }
        player = mp

        try {
            // On API 25, setAudioAttributes after prepare() can be flaky.
            // It's safest to just use default or the legacy stream type.
            if (Build.VERSION.SDK_INT < 21) {
                @Suppress("DEPRECATION")
                mp.setAudioStreamType(AudioManager.STREAM_MUSIC)
            } else {
                // Optional: many devices work fine without this line; skip if it ever throws IllegalStateException.
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build()
                )
            }

            mp.setOnCompletionListener { stop() }
            mp.start()
        } catch (t: Throwable) {
            Log.e("PromptPlayer", "Playback failed", t)
            stop()
        }
    }

    fun stop() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }
}
