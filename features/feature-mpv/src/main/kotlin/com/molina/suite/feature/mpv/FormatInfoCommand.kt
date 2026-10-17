package com.molina.suite.feature.mpv

import java.util.Locale

/**
 * Menyusun satu baris perintah Terminal untuk melihat daftar format sebuah tautan. Hanya tautan
 * http(s) tanpa spasi atau karakter kendali yang diterima; nilai dikutip untuk shell sehingga
 * tanda & ; $ ` atau kutip di dalam URL tidak pernah dianggap sintaks.
 */
internal object FormatInfoCommand {

    private const val MAX_URL_LENGTH = 2048

    /** Null bila [raw] bukan tautan http(s) yang aman diketik. */
    fun build(raw: String?): String? {
        val url = raw?.trim() ?: return null
        if (url.isEmpty() || url.length > MAX_URL_LENGTH) return null
        val lower = url.lowercase(Locale.ROOT)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
        if (url.any { it.code < 0x21 || it.code == 0x7f }) return null
        return "molina-ytdlp --no-playlist -F " + quote(url)
    }

    /** Kutip tunggal shell; kutip tunggal di dalam nilai ditulis sebagai '\\''. */
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
