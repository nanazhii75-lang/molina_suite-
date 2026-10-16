package com.molina.suite.mpv

/** Membedakan isi kolom pencarian: URL untuk diputar langsung, selain itu kata kunci. */
internal object SearchInput {

    private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://\\S+$")
    private val BARE_HOSTS = listOf(
        "www.", "youtu.be/", "youtube.com/", "m.youtube.com/", "music.youtube.com/"
    )

    /** URL siap putar bila [input] berupa URL; null bila harus dicari sebagai judul. */
    fun asUrl(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        if (SCHEME.matches(text)) return text
        val lower = text.lowercase()
        val host = BARE_HOSTS.firstOrNull { lower.startsWith(it) } ?: return null
        return if (text.length > host.length) "https://$text" else null
    }
}
