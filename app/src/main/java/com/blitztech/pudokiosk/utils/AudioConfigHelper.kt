package com.blitztech.pudokiosk.utils

import android.content.Context
import android.media.AudioManager
import android.util.Log

/**
 * Audio Configuration Helper
 * Manages audio system settings for kiosk mode while preserving notification capability
 */
object AudioConfigHelper {

    private const val TAG = "AudioConfigHelper"

    /**
     * Initialize audio system for kiosk mode
     * - Reduces system log verbosity
     * - Preserves notification sound capability
     * - Optimizes for hardware without speakers initially
     */
    fun initializeForKioskMode(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Set reasonable default volumes (not muted, for future notifications)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 5, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 3, 0)

            // Disable unnecessary audio streams that might cause routing logs
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 0, 0)

            Log.d(TAG, "Audio system configured for kiosk mode with notification support")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure audio system", e)
        }
    }

    /**
     * Prepare audio for notification sounds
     * Call this when you're ready to implement notifications
     */
    fun enableNotificationAudio(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Enable notification stream at appropriate volume
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 7, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 5, 0)

            Log.d(TAG, "Notification audio enabled")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable notification audio", e)
        }
    }

    /**
     * Check if audio system is ready for notifications
     */
    fun isNotificationAudioReady(context: Context): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION) > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check notification audio status", e)
            false
        }
    }
}