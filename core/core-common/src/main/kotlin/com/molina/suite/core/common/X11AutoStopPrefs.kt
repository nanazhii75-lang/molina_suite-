package com.molina.suite.core.common

import android.content.Context

/**
 * Pengaturan berhenti otomatis X11: berapa menit aplikasi boleh berada di latar belakang sebelum
 * desktop dan server dihentikan. Nol berarti mati. Dibaca modul X11, diubah lewat layar Setting.
 */
class X11AutoStopPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var minutes: Int
        get() = prefs.getInt(KEY, OFF).let { if (it in OPTIONS) it else OFF }
        set(value) {
            prefs.edit().putInt(KEY, if (value in OPTIONS) value else OFF).apply()
        }

    companion object {
        const val OFF = 0
        val OPTIONS = intArrayOf(OFF, 10, 30, 60)
        private const val FILE = "molina_x11_prefs"
        private const val KEY = "auto_stop_minutes"
    }
}
