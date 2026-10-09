package com.molina.suite.ui

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.molina.suite.R
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.EngineStatus

@ColorRes
fun EngineStatus.colorRes(): Int = when (this) {
    EngineStatus.RUNNING -> R.color.molina_status_on
    EngineStatus.STARTING -> R.color.molina_amber
    EngineStatus.ERROR -> R.color.molina_status_error
    EngineStatus.STOPPED, EngineStatus.UNAVAILABLE -> R.color.molina_status_off
}

@StringRes
fun EngineStatus.labelRes(): Int = when (this) {
    EngineStatus.UNAVAILABLE -> R.string.status_unavailable
    EngineStatus.STOPPED -> R.string.status_stopped
    EngineStatus.STARTING -> R.string.status_starting
    EngineStatus.RUNNING -> R.string.status_running
    EngineStatus.ERROR -> R.string.status_error
}

@StringRes
fun EngineId.labelRes(): Int = when (this) {
    EngineId.MPV -> R.string.nav_mpv
    EngineId.FILES -> R.string.nav_files
    EngineId.TERMINAL -> R.string.nav_terminal
    EngineId.CODE -> R.string.nav_code
    EngineId.SETTINGS -> R.string.nav_settings
}
