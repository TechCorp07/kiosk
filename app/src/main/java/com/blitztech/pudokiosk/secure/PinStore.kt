package com.blitztech.pudokiosk.secure

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

class PinStore(ctx: Context) {
    private val masterKey = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs = EncryptedSharedPreferences.create(
        ctx, "pins", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun setPin(userId: String, pin: String) {
        val h = hash(pin)
        prefs.edit().putString("pin_$userId", h).apply()
    }

    fun hasPin(userId: String): Boolean = prefs.contains("pin_$userId")

    fun verify(userId: String, pin: String): Boolean {
        val saved = prefs.getString("pin_$userId", null) ?: return false
        return saved == hash(pin)
    }

    private fun hash(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
