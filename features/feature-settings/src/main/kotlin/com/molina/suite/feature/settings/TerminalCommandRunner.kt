package com.molina.suite.feature.settings

import android.app.Activity
import com.molina.suite.core.common.Activities
import com.molina.suite.core.common.ActivityPriority
import com.molina.suite.core.common.ActivitySource
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.HostTabSwitcher
import com.molina.suite.core.common.TerminalInputResult
import kotlinx.coroutines.delay

/**
 * Mengetik satu baris ke sesi aktif tab Terminal lalu Enter. Berpindah ke tab Terminal
 * lebih dulu, dan menunggu sesi siap sampai 10 detik bila tab baru dibuka. Setiap pengiriman
 * dicatat ke logcat dengan label pemanggilnya; isi baris sengaja tidak dicatat karena bisa
 * memuat data pribadi.
 */
internal object TerminalCommandRunner {

    private const val MAX_ATTEMPTS = 20
    private const val RETRY_DELAY_MS = 500L
    private const val MAX_LABEL_CHARS = 80

    /**
     * Mengetik [line] ke Terminal dan mencatat hasilnya dengan [label]. Mengembalikan null bila
     * terkirim, atau alasan kegagalan. Panggil dari main thread.
     */
    suspend fun run(activity: Activity, label: String, line: String): String? =
        Activities.log.track(ActivitySource.SETTING, logName(label)) { entry ->
            val reason = deliver(activity, line)
            if (reason == null) {
                entry.note("diketik ke Terminal")
            } else {
                entry.end(false, "tidak terkirim ke Terminal: $reason", ActivityPriority.WARN)
            }
            reason
        }

    /** Label satu baris, dipotong, supaya log tetap rapi. */
    private fun logName(label: String): String =
        label.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            .take(MAX_LABEL_CHARS)
            .ifEmpty { "tanpa label" }

    private suspend fun deliver(activity: Activity, line: String): String? {
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
