package com.blitztech.pudokiosk.prefs

import android.content.Context

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    // Language
    fun getLocale(): String = p.getString("locale", "en") ?: "en"
    fun setLocale(code: String) { p.edit().putString("locale", code).apply() }

    // Scanner baud (default Honeywell often 115200)
    fun getScannerBaud(): Int = p.getInt("scanner_baud", 115200)
    fun setScannerBaud(v: Int) { p.edit().putInt("scanner_baud", v).apply() }
}
