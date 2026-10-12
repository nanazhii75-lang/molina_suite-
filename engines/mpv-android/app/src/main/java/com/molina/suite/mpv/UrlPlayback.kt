package com.molina.suite.mpv

import androidx.fragment.app.Fragment

/**
 * Pemeriksaan sebelum sebuah URL diputar. Modul fitur memasangnya saat startup; mesin mpv tidak
 * tahu apa yang diperiksa (alat yang dibutuhkan, dialog, dan sebagainya).
 */
fun interface UrlPlaybackGate {

    /**
     * Memanggil [proceed] satu kali bila [url] boleh diputar, dan tidak memanggilnya bila pengguna
     * membatalkan. Boleh dipanggil nanti, setelah dialog dijawab.
     */
    fun check(fragment: Fragment, url: String, proceed: () -> Unit)
}

/** Titik pertemuan longgar antara UI mpv dan pemeriksa URL. Tanpa pemeriksa, URL langsung diputar. */
object UrlPlayback {

    @Volatile
    private var gate: UrlPlaybackGate? = null

    fun install(newGate: UrlPlaybackGate) {
        gate = newGate
    }

    fun check(fragment: Fragment, url: String, proceed: () -> Unit) {
        val current = gate
        if (current == null) proceed() else current.check(fragment, url, proceed)
    }
}
