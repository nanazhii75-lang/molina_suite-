package com.molina.suite.feature.code

import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Paths

/** Validasi target perintah Terminal molina-open. */
internal object OpenInput {

    /**
     * Jalur berkas absolut yang sudah dinormalkan (tanpa "." dan ".."), atau null bila [raw]
     * kosong, tidak absolut, memuat karakter NUL, atau bukan berkas biasa menurut [isFile].
     */
    fun fileTarget(raw: String?, isFile: (File) -> Boolean = { it.isFile }): File? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.indexOf('\u0000') >= 0 || !text.startsWith("/")) return null
        val normalized = try {
            Paths.get(text).normalize().toString()
        } catch (e: InvalidPathException) {
            return null
        }
        val file = File(normalized)
        return if (isFile(file)) file else null
    }
}
