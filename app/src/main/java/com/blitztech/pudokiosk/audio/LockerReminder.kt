package com.blitztech.pudokiosk.audio

import android.content.Context
import kotlinx.coroutines.*

class LockerReminder(
    private val ctx: Context,
    private val locale: String // "en" | "sn" | "nd"
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val player = PromptPlayer(ctx)

    fun start() {
        scope.launch {
            repeat(3) { // play up to 3 reminders every 10 seconds
                delay(10_000)
                val res = when (locale) {
                    "sn" -> com.blitztech.pudokiosk.R.raw.locker_reminder_sn
                    "nd" -> com.blitztech.pudokiosk.R.raw.locker_reminder_nd
                    else -> com.blitztech.pudokiosk.R.raw.locker_reminder_en
                }
                try {
                    player.play(res)
                } catch (t: Throwable) {
                    android.util.Log.e("LockerReminder", "Failed to play reminder", t)
                }
            }
        }
    }

    fun stop() {
        player.stop()
        scope.cancel()
    }
}
