package com.molina.suite.mpv

/**
 * Mengambil URL dari teks bebas, misalnya teks Bagikan yang berisi judul + tautan
 * ("Video lucu https://vt.tiktok.com/abc/") atau isi clipboard.
 */
internal object SharedUrl {

    private val URL = Regex("(?i)\\b(?:https?|rtmps?|rtsp)://[^\\s<>\"'\u201C\u201D]+")
    private const val TRAILING = ".,;:!?'\"\u2019\u201D"

    /** URL pertama di [text] tanpa tanda baca penutup kalimat; null bila tidak ada. */
    fun extract(text: String?): String? {
        if (text.isNullOrBlank()) return null
        var url = URL.find(text)?.value ?: return null
        while (url.isNotEmpty() && shouldTrim(url)) {
            url = url.dropLast(1)
        }
        return if (url.substringAfter("://").isEmpty()) null else url
    }

    private fun shouldTrim(url: String): Boolean {
        val last = url.last()
        if (last in TRAILING) return true
        val open = when (last) {
            ')' -> '('
            ']' -> '['
            '}' -> '{'
            else -> return false
        }
        // Tanda kurung penutup dibuang hanya bila tidak berpasangan di dalam URL.
        return url.count { it == open } < url.count { it == last }
    }
}
