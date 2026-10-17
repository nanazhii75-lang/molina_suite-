package com.molina.suite.feature.mpv

import com.molina.suite.core.common.PlaybackBoard
import com.molina.suite.core.common.PlaybackSnapshot
import com.molina.suite.mpv.MPVLib
import com.molina.suite.mpv.MpvActivityListener
import com.molina.suite.mpv.MpvOpenKind

/**
 * Menerjemahkan kejadian mpv menjadi [PlaybackSnapshot] di [PlaybackBoard]. Mendengarkan mpv hanya
 * selama satu sesi pemutar (dari opened sampai closed); potret baru dipublikasikan setelah berkas
 * termuat dan dikosongkan saat pemutar ditutup.
 */
internal class MpvPlaybackPublisher(
    private val board: PlaybackBoard
) : MpvActivityListener, MPVLib.EventObserver {

    private val lock = Any()
    private var session = NO_SESSION
    private var loaded = false
    private var title = ""
    private var paused = false
    private var position = 0L
    private var duration = 0L
    private var playlistCount = 0L

    override fun opened(session: Long, kind: MpvOpenKind) {
        synchronized(lock) {
            resetLocked()
            this.session = session
        }
        MPVLib.addObserver(this)
    }

    override fun note(session: Long, text: String, error: Boolean) = Unit

    override fun closed(session: Long, played: Boolean, failure: String?) {
        synchronized(lock) {
            if (this.session != session) return
            this.session = NO_SESSION
            resetLocked()
        }
        MPVLib.removeObserver(this)
        board.publish(null)
    }

    override fun eventProperty(property: String) = Unit

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "time-pos" -> update { position = value }
            "playlist-count" -> update { playlistCount = value }
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        if (property == "pause") update { paused = value }
    }

    override fun eventProperty(property: String, value: String) {
        if (property == "media-title") update { title = value }
    }

    override fun eventProperty(property: String, value: Double) {
        if (property == "duration/full") update { duration = value.toLong() }
    }

    override fun event(eventId: Int) {
        if (eventId == MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED) update { loaded = true }
    }

    private inline fun update(change: () -> Unit) {
        val snapshot = synchronized(lock) {
            if (session == NO_SESSION) return
            change()
            if (!loaded) return
            PlaybackSnapshot(title, paused, position, duration, playlistCount > 1)
        }
        board.publish(snapshot)
    }

    private fun resetLocked() {
        loaded = false
        title = ""
        paused = false
        position = 0L
        duration = 0L
        playlistCount = 0L
    }

    private companion object {
        const val NO_SESSION = 0L
    }
}
