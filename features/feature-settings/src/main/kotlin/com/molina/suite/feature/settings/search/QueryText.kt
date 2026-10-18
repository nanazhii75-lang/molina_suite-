package com.molina.suite.feature.settings.search

/** Merapikan kata kunci sebelum dikirim: tanpa karakter kontrol, spasi rapat, panjang dibatasi. */
object QueryText {

    const val MAX_LENGTH = 256

    private val spaces = Regex("\\s+")

    fun normalize(raw: String): String? {
        val cleaned = buildString(raw.length) {
            for (ch in raw) {
                if (ch.isWhitespace()) append(' ') else if (!Character.isISOControl(ch)) append(ch)
            }
        }
        val query = spaces.replace(cleaned, " ").trim().take(MAX_LENGTH).trim()
        return query.ifEmpty { null }
    }
}
