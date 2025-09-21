package com.blitztech.pudokiosk.secure

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import android.util.Base64

class SecretStore(ctx: Context) {
    private val masterKey = MasterKey.Builder(ctx)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        ctx,
        "secrets",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun dbPassphrase(): ByteArray {
        val key = "db_passphrase_b64"
        val existing = prefs.getString(key, null)
        if (existing != null) return Base64.decode(existing, Base64.NO_WRAP)
        val rnd = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(key, Base64.encodeToString(rnd, Base64.NO_WRAP)).apply()
        return rnd
    }
}
