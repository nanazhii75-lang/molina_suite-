package com.molina.suite.feature.mpv

import com.molina.suite.core.common.Activities
import com.molina.suite.core.common.ActivityLog
import com.molina.suite.core.common.ActivityPriority
import com.molina.suite.core.common.ActivitySource
import com.molina.suite.mpv.MpvActivityListener
import com.molina.suite.mpv.MpvOpenKind
import java.util.concurrent.ConcurrentHashMap

/**
 * Menulis pemutaran mpv sebagai aktivitas [ActivityLog] (sumber mpv). Yang dicatat: jenis
 * pembukaan, berkas termuat, galat dari mpv, dan hasil akhir. Nama berkas tidak dicatat dan
 * setiap URL di pesan galat diganti "<url>".
 */
internal class MpvActivityLogger(
    private val log: ActivityLog = Activities.log
) : MpvActivityListener {

    private val running = ConcurrentHashMap<Long, ActivityLog.Activity>()

    override fun opened(session: Long, kind: MpvOpenKind) {
        running[session] = log.begin(ActivitySource.MPV, title(kind))
    }

    override fun note(session: Long, text: String, error: Boolean) {
        val priority = if (error) ActivityPriority.WARN else ActivityPriority.INFO
        running[session]?.note(MpvLogText.redact(text), priority)
    }

    override fun closed(session: Long, played: Boolean, failure: String?) {
        val activity = running.remove(session) ?: return
        when {
            failure != null -> activity.end(false, MpvLogText.redact(failure))
            played -> activity.end(true)
            else -> activity.end(true, "ditutup sebelum berkas termuat")
        }
    }

    private fun title(kind: MpvOpenKind): String = when (kind) {
        MpvOpenKind.LOCAL -> "Putar berkas lokal"
        MpvOpenKind.URL -> "Putar URL"
        MpvOpenKind.SEARCH -> "Putar hasil pencarian"
        MpvOpenKind.OTHER -> "Putar media"
    }
}
