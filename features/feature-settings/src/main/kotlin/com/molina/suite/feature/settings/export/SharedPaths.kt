package com.molina.suite.feature.settings.export

import com.molina.suite.core.common.ScriptRunLine

/**
 * Jalur berkas yang boleh dipakai skrip konversi. Hanya penyimpanan bersama yang terlihat dari
 * Debian (/sdcard); jalur lain ditolak supaya skrip tidak pernah menyentuh folder privat aplikasi.
 */
internal object SharedPaths {

    private const val SHARED_PREFIX = "/sdcard/"

    /**
     * Jalur di penyimpanan bersama dalam bentuk yang terlihat dari Debian, atau null bila tidak
     * valid: tanpa baris baru, tanpa karakter NUL, tanpa segmen "..", dan harus menunjuk berkas.
     */
    fun resolve(path: String): String? {
        if (path.any { it == '\n' || it == '\r' || it == '\u0000' }) return null
        val normalized = ScriptRunLine.normalizePath(path)
        if (!normalized.startsWith(SHARED_PREFIX) || normalized.endsWith("/")) return null
        if (normalized.split('/').any { it == ".." }) return null
        return normalized
    }
}
