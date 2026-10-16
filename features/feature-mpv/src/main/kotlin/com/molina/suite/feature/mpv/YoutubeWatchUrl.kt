package com.molina.suite.feature.mpv

/**
 * Mengenali URL video YouTube dan mengambil ID 11 karakternya. Hanya https dan hanya host
 * YouTube yang dikenal; semua bentuk lain ditolak (null) sehingga jembatan NewPipe tidak bisa
 * dipakai untuk mengambil alamat sembarang.
 */
internal object YoutubeWatchUrl {

    private const val ID = "([A-Za-z0-9_-]{11})"
    private const val HOST = "(?:www\\.|m\\.|music\\.)?youtube\\.com"

    private val WATCH = Regex("^https://$HOST/watch\\?(?:[^#]*&)?v=$ID(?:[&#].*)?$")
    private val PATH_FORMS = Regex("^https://$HOST/(?:shorts|live|embed)/$ID(?:[/?&#].*)?$")
    private val SHORT = Regex("^https://youtu\\.be/$ID(?:[/?&#].*)?$")

    fun videoId(url: String): String? {
        val text = url.trim()
        if (text.length > MAX_URL_LENGTH) return null
        for (pattern in listOf(WATCH, PATH_FORMS, SHORT)) {
            pattern.matchEntire(text)?.let { return it.groupValues[1] }
        }
        return null
    }

    private const val MAX_URL_LENGTH = 2048
}
