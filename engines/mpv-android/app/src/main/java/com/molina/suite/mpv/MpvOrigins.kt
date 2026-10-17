package com.molina.suite.mpv

/**
 * Menyaring alamat yang dibuka mpv menjadi "asal" saja (skema://host[:port]). Jalur, query
 * (ID video, token), dan user:password dibuang di sini, sebelum keluar dari engine, sehingga
 * pendengar tidak pernah menerima URL utuh.
 */
object MpvOrigins {

    const val MAX_PER_TEXT = 8

    private val URL = Regex("""https?://[^\s;"'|<>]+""", RegexOption.IGNORE_CASE)

    /**
     * Asal tiap URL http(s) di [text], tanpa duplikat, paling banyak [MAX_PER_TEXT]. Berlaku juga
     * untuk alamat gabungan (edl://) yang memuat beberapa URL. Selain http(s) diabaikan.
     */
    fun extract(text: String?): List<String> {
        if (text.isNullOrEmpty()) return emptyList()
        val found = LinkedHashSet<String>()
        for (match in URL.findAll(text)) {
            origin(match.value)?.let { found.add(it) }
            if (found.size >= MAX_PER_TEXT) break
        }
        return found.toList()
    }

    private fun origin(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = url.substring(0, schemeEnd).lowercase()
        val authority = url.substring(schemeEnd + 3)
            .takeWhile { it != '/' && it != '?' && it != '#' }
            .substringAfterLast('@')
        if (authority.isEmpty()) return null
        return "$scheme://$authority"
    }
}
