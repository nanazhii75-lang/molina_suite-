package com.molina.suite.feature.settings.library

sealed interface LibraryResult<out T> {
    data class Ok<out T>(val value: T) : LibraryResult<T>
    data class Failure(val reason: String) : LibraryResult<Nothing>
}

/** Penyimpanan entri Library. Semua metode memblokir I/O: panggil dari Dispatchers.IO. */
interface LibraryRepository {
    fun list(): LibraryResult<List<LibraryEntry>>
    fun upsert(entry: LibraryEntry): LibraryResult<Unit>
    fun remove(id: String): LibraryResult<Unit>
}
