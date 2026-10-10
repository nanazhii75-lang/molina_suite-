package com.molina.suite.feature.settings.library

import java.util.UUID

/** Tempat entri dijalankan: shell host terminal, atau di dalam Debian (proot). */
enum class RunMode(val key: String) {
    HOST("host"),
    DEBIAN("debian");

    companion object {
        fun fromKey(key: String?): RunMode = values().firstOrNull { it.key == key } ?: HOST
    }
}

/**
 * Satu entri Library: tautan, perintah, atau teks bebas yang bisa dijalankan di terminal.
 * [content] boleh memuat placeholder seperti {URL}, yang diisi lewat dialog saat dijalankan.
 */
data class LibraryEntry(
    val id: String,
    val name: String,
    val content: String,
    val note: String = "",
    val runIn: RunMode = RunMode.HOST,
    val builtin: Boolean = false
) {
    companion object {
        fun create(
            name: String,
            content: String,
            note: String = "",
            runIn: RunMode = RunMode.HOST
        ): LibraryEntry = LibraryEntry(UUID.randomUUID().toString(), name, content, note, runIn)
    }
}
