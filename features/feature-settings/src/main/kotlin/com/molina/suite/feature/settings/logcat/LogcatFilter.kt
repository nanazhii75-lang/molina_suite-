package com.molina.suite.feature.settings.logcat

/**
 * Penyaring entri untuk tampilan: tingkat minimum dan kata kunci. [query] dipecah per spasi dan
 * semua kata harus ada di tag atau pesan (huruf besar/kecil tidak dibedakan); kosong berarti semua.
 */
internal data class LogcatFilter(
    val minLevel: LogcatLevel = LogcatLevel.VERBOSE,
    val query: String = ""
) {
    private val terms: List<String> = query.lowercase().split(' ').filter { it.isNotEmpty() }

    fun matches(entry: LogcatEntry): Boolean {
        if (entry.severity < minLevel) return false
        if (terms.isEmpty()) return true
        val haystack = (entry.tag + ' ' + entry.message).lowercase()
        return terms.all { haystack.contains(it) }
    }
}
