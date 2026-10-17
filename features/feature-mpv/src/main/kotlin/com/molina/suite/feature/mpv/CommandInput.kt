package com.molina.suite.feature.mpv

import java.io.File

/** Sumber yang boleh diputar dari perintah Terminal. */
internal enum class PlaySource { URL, LOCAL }

internal data class PlayTarget(val value: String, val source: PlaySource)

/**
 * Memeriksa masukan dari perintah Terminal sebelum diteruskan ke pemutar atau pencarian. Hanya
 * URL stream biasa dan berkas lokal yang ada yang diterima; skema lain (ytdl://, edl://, fd://,
 * file://, dan sebagainya) ditolak.
 */
internal object CommandInput {

    private const val MAX_TARGET = 2048
    private const val MAX_QUERY = 200
    private val STREAM_URL = Regex("^(?:https?|rtmps?|rtsp)://\\S+$", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("\\s+")

    fun playTarget(
        raw: String?,
        isFile: (String) -> Boolean = { path -> File(path).let { it.isFile && it.canRead() } }
    ): PlayTarget? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.length > MAX_TARGET || text.any { it.isISOControl() }) return null
        if (STREAM_URL.matches(text)) return PlayTarget(text, PlaySource.URL)
        if (text.startsWith("/") && isFile(text)) return PlayTarget(text, PlaySource.LOCAL)
        return null
    }

    /** Kata kunci bersih: karakter kendali dibuang, spasi dirapikan, dipotong di 200 karakter. */
    fun searchQuery(raw: String?): String? {
        val cleaned = raw.orEmpty()
            .filter { !it.isISOControl() || it.isWhitespace() }
            .trim()
            .replace(SPACES, " ")
            .take(MAX_QUERY)
            .trim()
        return cleaned.ifEmpty { null }
    }
}
