package com.molina.suite.feature.code

import java.nio.charset.Charset

internal data class CodeTextStats(
    val lines: Int,
    val words: Int,
    val characters: Int,
    val bytes: Long,
    val selectedCharacters: Int
)

/** Penghitung statistik teks; murni fungsi sehingga aman dijalankan di thread latar belakang. */
internal object CodeTextStatistics {

    private const val BOM_SIZE = 3

    fun compute(text: String, charset: Charset, hasBom: Boolean, selectedCharacters: Int): CodeTextStats {
        var lines = 1
        var words = 0
        var inWord = false
        for (ch in text) {
            if (ch == '\n') lines++
            if (Character.isWhitespace(ch)) {
                inWord = false
            } else if (!inWord) {
                inWord = true
                words++
            }
        }
        val bytes = text.toByteArray(charset).size.toLong() + if (hasBom) BOM_SIZE else 0
        return CodeTextStats(lines, words, text.length, bytes, selectedCharacters)
    }
}
