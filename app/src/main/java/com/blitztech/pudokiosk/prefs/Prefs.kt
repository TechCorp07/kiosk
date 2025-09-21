package com.blitztech.pudokiosk.prefs

import android.content.Context

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    // Language
    fun getLocale(): String = p.getString("locale", "en") ?: "en"
    fun setLocale(code: String) { p.edit().putString("locale", code).apply() }

    // Hardware simulation (RS485/Scanner)
    fun isSimHardware(): Boolean = p.getBoolean("sim_hw", true)
    fun setSimHardware(v: Boolean) { p.edit().putBoolean("sim_hw", v).apply() }

    // Use IM30 backup payment path
    fun isUseBackupIM30(): Boolean = p.getBoolean("use_im30", false)
    fun setUseBackupIM30(v: Boolean) { p.edit().putBoolean("use_im30", v).apply() }

    // Scanner baud (default Honeywell often 9600)
    fun getScannerBaud(): Int = p.getInt("scanner_baud", 9600)
    fun setScannerBaud(v: Int) { p.edit().putInt("scanner_baud", v).apply() }
}
