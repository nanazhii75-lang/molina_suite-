package com.molina.suite.feature.code

import java.util.Locale

/** Jenis berkas yang bisa ditinjau di halaman Tinjauan, dikenali dari ekstensi nama berkas. */
internal enum class CodePreviewKind(val sourceExtension: String) {
    MARKDOWN("md"),
    HTML("html"),
    SVG("svg");

    companion object {
        private val BY_EXTENSION = mapOf(
            "md" to MARKDOWN,
            "markdown" to MARKDOWN,
            "mdx" to MARKDOWN,
            "html" to HTML,
            "htm" to HTML,
            "xhtml" to HTML,
            "svg" to SVG
        )

        /** Jenis Tinjauan untuk [fileName]; null bila ekstensinya tidak bisa ditinjau. */
        fun of(fileName: String): CodePreviewKind? {
            val extension = CodeExtensions.extensionOf(fileName) ?: return null
            return BY_EXTENSION[extension.lowercase(Locale.ROOT)]
        }

        fun fromName(name: String?): CodePreviewKind? = values().firstOrNull { it.name == name }
    }
}
