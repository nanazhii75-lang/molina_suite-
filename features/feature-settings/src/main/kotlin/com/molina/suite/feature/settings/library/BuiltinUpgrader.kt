package com.molina.suite.feature.settings.library

/**
 * Memperbarui entri bawaan yang isinya masih sama persis dengan versi lama bawaan aplikasi.
 * Entri yang sudah diubah pengguna tidak disentuh, dan entri yang sudah dihapus tidak
 * dikembalikan (itu tugas "Pulihkan").
 */
internal object BuiltinUpgrader {

    /** Isi lama yang boleh diganti otomatis, dikunci dengan id entri. */
    private val superseded: Map<String, List<String>> = mapOf(
        "builtin-tool-versions" to listOf(
            "python3 --version; ffmpeg -version | head -n 1; aria2c --version | head -n 1; " +
                "yt-dlp --version; java -version 2>&1 | head -n 1"
        )
    )

    /** Memblokir I/O. Mengembalikan jumlah entri yang diperbarui. */
    fun apply(repository: LibraryRepository): LibraryResult<Int> {
        val stored = when (val result = repository.list()) {
            is LibraryResult.Failure -> return result
            is LibraryResult.Ok -> result.value
        }
        var updated = 0
        for (builtin in BuiltinLibrary.entries) {
            val oldContents = superseded[builtin.id] ?: continue
            val current = stored.firstOrNull { it.id == builtin.id } ?: continue
            if (current.content == builtin.content || current.content !in oldContents) continue
            when (val result = repository.upsert(current.copy(content = builtin.content, note = builtin.note))) {
                is LibraryResult.Failure -> return result
                is LibraryResult.Ok -> updated++
            }
        }
        val iptv = stored.firstOrNull { it.id == IptvIndonesia.ENTRY_ID }
        if (iptv != null && iptv.builtin && iptv.content.contains("MOLINA_IPTV_EOF")) {
            val fresh = iptv.copy(content = IptvIndonesia.entry.content, note = IptvIndonesia.entry.note)
            when (val result = repository.upsert(fresh)) {
                is LibraryResult.Failure -> return result
                is LibraryResult.Ok -> updated++
            }
        }
        return LibraryResult.Ok(updated)
    }
}
