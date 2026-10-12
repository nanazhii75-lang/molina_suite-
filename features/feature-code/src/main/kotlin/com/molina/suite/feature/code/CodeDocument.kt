package com.molina.suite.feature.code

import java.io.File
import java.nio.charset.Charset

/**
 * Dokumen yang sedang disunting. [file] bernilai null untuk dokumen
 * "Tidak berjudul" yang belum pernah disimpan.
 */
internal data class CodeDocument(
    val file: File?,
    val charset: Charset,
    val hasBom: Boolean,
    val dirty: Boolean
) {
    companion object {
        fun untitled(): CodeDocument = CodeDocument(null, Charsets.UTF_8, hasBom = false, dirty = false)
    }
}
