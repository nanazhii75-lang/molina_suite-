package com.molina.suite.mpv

import android.app.Activity

/** Pembuka "Info format" yang dipasang oleh modul fitur; engine mpv tidak mengenal Terminal. */
fun interface FormatInfoOpener {
    /** @return true bila permintaan diteruskan, false bila tidak bisa dijalankan. */
    fun open(host: Activity, url: String): Boolean
}

object FormatInfoEntry {
    @Volatile
    private var opener: FormatInfoOpener? = null

    fun install(opener: FormatInfoOpener) {
        this.opener = opener
    }

    /** Hanya tautan http(s) yang bisa dibaca yt-dlp; berkas lokal dan skema lain tidak. */
    fun isSupported(path: String?): Boolean {
        if (path == null) return false
        return path.startsWith("http://", ignoreCase = true) ||
            path.startsWith("https://", ignoreCase = true)
    }

    fun open(host: Activity, url: String): Boolean {
        if (!isSupported(url)) return false
        return opener?.open(host, url) ?: false
    }
}
