package com.molina.suite.feature.settings.library

import java.util.UUID

/** Satu entri Library: tautan, perintah, atau teks bebas yang bisa dijalankan di terminal. */
data class LibraryEntry(
    val id: String,
    val name: String,
    val content: String,
    val note: String = ""
) {
    companion object {
        fun create(name: String, content: String, note: String = ""): LibraryEntry =
            LibraryEntry(UUID.randomUUID().toString(), name, content, note)
    }
}
