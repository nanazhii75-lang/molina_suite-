package com.molina.suite.feature.mpv

/** Pembersih teks sebelum masuk log aktivitas. */
internal object MpvLogText {

    private val URL = Regex("""[A-Za-z][A-Za-z0-9+.\-]*://\S+""")

    /** Mengganti setiap URL dengan "<url>"; pesan galat mpv dan yt-dlp sering memuat tautan lengkap. */
    fun redact(text: String): String = URL.replace(text, "<url>")
}
