package com.molina.suite.core.common

import android.content.Context

/**
 * Saklar tiap [IntegrationRoute]; bawaannya menyala. Dibaca modul yang menegakkan lintasan
 * (Terminal ke Code dan mpv, Code ke mpv), diubah lewat layar Integrasi di Setting.
 */
class IntegrationSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(route: IntegrationRoute): Boolean = prefs.getBoolean(route.key, true)

    fun setEnabled(route: IntegrationRoute, enabled: Boolean) {
        prefs.edit().putBoolean(route.key, enabled).apply()
    }

    private companion object {
        const val FILE = "molina_integration_prefs"
    }
}
