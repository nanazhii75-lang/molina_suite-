package com.molina.suite.feature.settings

import android.app.Activity
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.HostTabSwitcher
import com.molina.suite.core.common.TerminalInputResult
import kotlinx.coroutines.delay

/**
 * Mengetik satu baris ke sesi aktif tab Terminal lalu Enter. Berpindah ke tab Terminal
 * lebih dulu, dan menunggu sesi siap sampai 10 detik bila tab baru dibuka.
 */
internal object TerminalCommandRunner {

    private const val MAX_ATTEMPTS = 20
    private const val RETRY_DELAY_MS = 500L

    /** Mengembalikan null bila terkirim, atau alasan kegagalan. Panggil dari main thread. */
    suspend fun run(activity: Activity, line: String): String? {
        if (line.any { it == '\n' || it == '\r' }) {
            return activity.getString(R.string.library_error_multiline)
        }
        val switcher = activity as? HostTabSwitcher
            ?: return activity.getString(R.string.library_error_no_switcher)
        val sender = SettingsDependencies.terminalInput()

        var result = sender.sendLine(line)
        switcher.showEngine(EngineId.TERMINAL)
        var attempts = 0
        while (result is TerminalInputResult.Rejected && attempts < MAX_ATTEMPTS) {
            delay(RETRY_DELAY_MS)
            result = sender.sendLine(line)
            attempts++
        }
        return (result as? TerminalInputResult.Rejected)?.reason
    }
}
