package com.molina.suite.ui

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.molina.suite.R
import com.molina.suite.core.common.DaemonId
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.EngineStatus

@ColorRes
fun EngineStatus.colorRes(): Int = when (this) {
    EngineStatus.RUNNING -> R.color.molina_status_on
    EngineStatus.STARTING -> R.color.molina_amber
    EngineStatus.ERROR -> R.color.molina_status_error
    EngineStatus.STOPPED, EngineStatus.UNAVAILABLE -> R.color.molina_status_off
}

/** Warna titik header: tiap daemon punya warna sendiri saat RUNNING; status lain memakai warna umum. */
@ColorRes
fun EngineStatus.daemonColorRes(id: DaemonId): Int =
    if (this == EngineStatus.RUNNING) {
        when (id) {
            DaemonId.TERMINAL -> R.color.daemon_terminal
            DaemonId.MPV -> R.color.daemon_mpv
            DaemonId.CODE -> R.color.molina_status_on
        }
    } else {
        colorRes()
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
    EngineId.TERMINAL -> R.string.nav_terminal
    EngineId.CODE -> R.string.nav_code
    EngineId.SETTINGS -> R.string.nav_settings
}
