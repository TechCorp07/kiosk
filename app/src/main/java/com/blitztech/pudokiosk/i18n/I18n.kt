package com.blitztech.pudokiosk.i18n

import android.content.Context
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.Locale

class I18n(private val ctx: Context) {
    private val cache = mutableMapOf<String, JSONObject>()
    var currentLocale: String = "en"; private set

    fun load(locale: String) {
        val asset = "i18n/$locale.json"
        val json = ctx.assets.open(asset).use { it.readBytes().toString(Charset.forName("UTF-8")) }
        cache[locale] = JSONObject(json)
        currentLocale = locale
    }

    fun t(key: String, fallback: String = key): String {
        val obj = cache[currentLocale] ?: return fallback
        return obj.optString(key, fallback)
    }

    companion object {
        fun localeFromDevice(): String {
            val lang = Locale.getDefault().language
            return when (lang) {
                "sn" -> "sn"
                "nd" -> "nd"
                else -> "en"
            }
        }
    }
}
