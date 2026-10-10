package com.molina.suite.feature.settings.library

sealed interface LibraryResult<out T> {
    data class Ok<out T>(val value: T) : LibraryResult<T>
    data class Failure(val reason: String) : LibraryResult<Nothing>
}

/** Penyimpanan entri Library. Semua metode memblokir I/O: panggil dari Dispatchers.IO. */
interface LibraryRepository {
    /** True bila berkas library sudah pernah dibuat. */
    fun isInitialized(): Boolean

    fun list(): LibraryResult<List<LibraryEntry>>
    fun upsert(entry: LibraryEntry): LibraryResult<Unit>
    fun remove(id: String): LibraryResult<Unit>

    /** Menambahkan entri dari [entries] yang id-nya belum ada. Mengembalikan jumlah yang ditambahkan. */
    fun addMissing(entries: List<LibraryEntry>): LibraryResult<Int>
}
