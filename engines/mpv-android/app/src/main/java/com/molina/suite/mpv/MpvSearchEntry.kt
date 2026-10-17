package com.molina.suite.mpv

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Titik pertemuan longgar untuk pencarian dari luar layar utama mpv (mis. perintah Terminal
 * molina-cari). Modul fitur meminta lewat [request]; layar utama mpv yang menjalankan pencarian
 * seperti kolom pencarian biasa. Permintaan yang belum punya penerima ditahan sebentar, lalu
 * dibuang agar tidak muncul tiba-tiba kemudian.
 */
object MpvSearchEntry {

    private const val MAX_AGE_MS = 15_000L

    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private var pending: String? = null
    private var requestedAt = 0L
    private var receiver: ((String) -> Unit)? = null

    /** Meminta pencarian [query]; boleh dipanggil dari thread mana pun. */
    fun request(query: String) {
        synchronized(lock) {
            pending = query
            requestedAt = SystemClock.elapsedRealtime()
        }
        deliver()
    }

    internal fun attach(newReceiver: (String) -> Unit) {
        synchronized(lock) { receiver = newReceiver }
        deliver()
    }

    internal fun detach(oldReceiver: (String) -> Unit) {
        synchronized(lock) {
            if (receiver === oldReceiver) receiver = null
        }
    }

    private fun deliver() {
        val job: Pair<(String) -> Unit, String> = synchronized(lock) {
            val target = receiver ?: return
            val query = pending ?: return
            pending = null
            if (SystemClock.elapsedRealtime() - requestedAt > MAX_AGE_MS) return
            target to query
        }
        main.post { job.first(job.second) }
    }
}
