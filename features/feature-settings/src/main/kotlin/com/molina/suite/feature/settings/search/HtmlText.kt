package com.molina.suite.feature.settings.search

/** Mengubah potongan HTML dari pihak luar menjadi teks polos yang aman ditampilkan. */
object HtmlText {

    private val tag = Regex("<[^>]*>")
    private val entity = Regex("&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|[a-zA-Z]{2,8});")
    private val spaces = Regex("\\s+")

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "ndash" to "\u2013", "mdash" to "\u2014", "hellip" to "\u2026", "laquo" to "\u00AB",
        "raquo" to "\u00BB", "lsquo" to "\u2018", "rsquo" to "\u2019", "ldquo" to "\u201C",
        "rdquo" to "\u201D", "copy" to "\u00A9", "middot" to "\u00B7", "bull" to "\u2022"
    )

    fun decode(text: String): String = entity.replace(text) { match ->
        val body = match.groupValues[1]
        if (body.startsWith("#")) {
            val code = if (body[1] == 'x' || body[1] == 'X') {
                body.substring(2).toIntOrNull(16)
            } else {
                body.substring(1).toIntOrNull()
            }
            if (code != null && code > 0 && Character.isValidCodePoint(code)) {
                String(Character.toChars(code))
            } else {
                ""
            }
        } else {
            named[body] ?: match.value
        }
    }

    /** Membuang tag, mendekode entitas, membuang karakter kontrol dan pengarah teks, merapatkan spasi. */
    fun plain(html: String): String {
        val decoded = decode(tag.replace(html, ""))
        val filtered = buildString(decoded.length) {
            for (ch in decoded) {
                if (ch.isWhitespace()) {
                    append(' ')
                } else if (!Character.isISOControl(ch) && !isBidiControl(ch)) {
                    append(ch)
                }
            }
        }
        return spaces.replace(filtered, " ").trim()
    }

    /** Karakter pengarah teks (U+202A..202E, U+2066..2069) dipakai untuk memalsukan tampilan alamat. */
    private fun isBidiControl(ch: Char): Boolean =
        ch in '\u202A'..'\u202E' || ch in '\u2066'..'\u2069' || ch == '\u200E' || ch == '\u200F'
}
