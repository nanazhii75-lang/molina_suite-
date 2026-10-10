package com.molina.suite.feature.code

import java.nio.charset.Charset

/** Enkode yang bisa dipilih untuk penyimpanan berkas. */
internal enum class CodeEncoding(val label: String, val charset: Charset, val bom: Boolean) {
    UTF8("UTF-8", Charsets.UTF_8, false),
    UTF8_BOM("UTF-8 dengan BOM", Charsets.UTF_8, true),
    LATIN1("ISO-8859-1 (Latin-1)", Charsets.ISO_8859_1, false),
    WINDOWS_1252("Windows-1252", Charset.forName("windows-1252"), false),
    ASCII("US-ASCII", Charsets.US_ASCII, false)
}
