package com.molina.suite.mpv

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Mengamati satu pemutaran di [MPVActivity] dan melaporkannya ke [MpvActivityListener]: berkas
 * termuat, galat dari mpv (dibatasi jumlahnya), dan penutupan. Dibuat lewat
 * [MpvActivityReporter.watch], didaftarkan sebagai pengamat log mpv, dan wajib ditutup dengan
 * [close]. Kegagalan pendengar tidak pernah mengganggu pemutar.
 */
internal class MpvPlaybackWatcher(
    private val listener: MpvActivityListener,
    kind: MpvOpenKind
) : MPVLib.LogObserver {

    private val session = SESSIONS.incrementAndGet()
    private val loaded = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val errors = AtomicInteger(0)
    private val firstErrorLock = Any()
    private var firstError: String? = null

    init {
        deliver { listener.opened(session, kind) }
    }

    /** Dipanggil dari [MPVActivity.event] untuk setiap kejadian mpv. */
    fun onEvent(eventId: Int) {
        if (eventId == MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED && loaded.compareAndSet(false, true)) {
            deliver { listener.note(session, "berkas termuat", false) }
        }
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
        if (closed.get() || level > MPVLib.mpvLogLevel.MPV_LOG_LEVEL_ERROR) return
        val body = text.trim()
        if (body.isEmpty()) return
        val line = if (prefix.isEmpty()) body else "$prefix: $body"
        synchronized(firstErrorLock) {
            if (firstError == null) firstError = line
        }
        val count = errors.incrementAndGet()
        when {
            count <= MAX_ERROR_NOTES -> deliver { listener.note(session, line, true) }
            count == MAX_ERROR_NOTES + 1 ->
                deliver { listener.note(session, "galat berikutnya dari mpv tidak dicatat", true) }
        }
    }

    /** Menutup pemutaran; panggilan kedua dan seterusnya diabaikan. */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        val played = loaded.get()
        val failure = if (played) null else synchronized(firstErrorLock) { firstError }
        deliver { listener.closed(session, played, failure) }
    }

    private inline fun deliver(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            // Pelaporan yang gagal tidak boleh merusak pemutaran.
        }
    }

    private companion object {
        const val MAX_ERROR_NOTES = 5
        val SESSIONS = AtomicLong()
    }
}
