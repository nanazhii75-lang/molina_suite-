package com.molina.suite.feature.settings.library

import com.molina.suite.feature.settings.packages.PackageCatalog
import com.molina.suite.feature.settings.packages.PackageSpec

/**
 * Paket Debian yang dibutuhkan entri bawaan, dikunci dengan id entri lalu id [PackageSpec].
 * Entri buatan sendiri tidak punya syarat. Entri yt-dlp sengaja tidak tercantum karena
 * deteksi yt-dlp di menu Paket mengikuti venv /opt/ytdlp, bukan yt-dlp apa pun di PATH.
 */
internal object LibraryRequirements {

    private val requirements: Map<String, List<String>> = mapOf(
        "builtin-aria2-download" to listOf("aria2"),
        "builtin-kotlin-check" to listOf("jdk21")
    )

    /** Id paket yang dibutuhkan entri; kosong bila tidak ada syarat. */
    fun of(entryId: String): List<String> = requirements[entryId].orEmpty()

    /** Paket yang pasti belum terpasang. Status tidak diketahui tidak dianggap kurang. */
    fun missing(entryId: String, installed: Map<String, Boolean>): List<PackageSpec> =
        of(entryId)
            .mapNotNull { id -> PackageCatalog.all.firstOrNull { it.id == id } }
            .filter { installed[it.id] == false }

    fun missingByEntry(installed: Map<String, Boolean>): Map<String, List<PackageSpec>> {
        val result = LinkedHashMap<String, List<PackageSpec>>()
        for (entryId in requirements.keys) {
            val gap = missing(entryId, installed)
            if (gap.isNotEmpty()) result[entryId] = gap
        }
        return result
    }
}
