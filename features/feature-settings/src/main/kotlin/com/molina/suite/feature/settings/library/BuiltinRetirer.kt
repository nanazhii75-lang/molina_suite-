package com.molina.suite.feature.settings.library

/**
 * Menghapus entri bawaan lama yang fungsinya kini punya menu sendiri (Paket, Debian, Pusat
 * Kontrol, Cek Jaringan). Hanya entri yang masih bawaan dan isinya sama persis dengan versi
 * lama yang dihapus; entri yang sudah diubah pengguna dibiarkan.
 */
internal object BuiltinRetirer {

    /** Memblokir I/O. Mengembalikan jumlah entri yang dihapus. */
    fun apply(repository: LibraryRepository): LibraryResult<Int> {
        val stored = when (val result = repository.list()) {
            is LibraryResult.Failure -> return result
            is LibraryResult.Ok -> result.value
        }
        val byId = stored.associateBy { it.id }
        var removed = 0
        for (old in BuiltinLibrary.retiredEntries) {
            val current = byId[old.id] ?: continue
            if (!current.builtin || current.content != old.content) continue
            when (val result = repository.remove(old.id)) {
                is LibraryResult.Failure -> return result
                is LibraryResult.Ok -> removed++
            }
        }
        return LibraryResult.Ok(removed)
    }
}
