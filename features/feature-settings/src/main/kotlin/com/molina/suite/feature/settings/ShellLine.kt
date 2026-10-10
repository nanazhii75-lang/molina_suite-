package com.molina.suite.feature.settings

import com.molina.suite.feature.settings.library.LibraryEntry
import com.molina.suite.feature.settings.library.RunMode

/** Placeholder bertanda kurung kurawal huruf besar, mis. {URL}. ${VAR} milik shell tidak dihitung. */
internal object PlaceholderTemplate {
    private val pattern = Regex("""(?<!\$)\{([A-Z][A-Z0-9_]*)\}""")

    /** Nama placeholder unik sesuai urutan kemunculan. */
    fun names(content: String): List<String> =
        pattern.findAll(content).map { it.groupValues[1] }.distinct().toList()

    fun fill(content: String, values: Map<String, String>): String =
        pattern.replace(content) { match -> values[match.groupValues[1]] ?: match.value }
}

/** Menyusun satu baris perintah siap ketik untuk terminal. */
internal object ShellLine {

    /** Membungkus [value] dengan tanda kutip tunggal shell. */
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /**
     * Menjalankan [inner] di dalam Debian. Dari shell host dipakai proot-distro; bila sudah
     * berada di dalam Debian (proot-distro tidak ada) perintah dijalankan langsung.
     */
    fun wrapForDebian(inner: String): String {
        val quoted = quote(inner)
        return "if command -v proot-distro >/dev/null 2>&1; then " +
            "proot-distro login debian -- bash -c $quoted; else bash -c $quoted; fi"
    }

    /** [values] diisikan ke placeholder dengan quote shell otomatis. */
    fun build(entry: LibraryEntry, values: Map<String, String>): String {
        val inner = PlaceholderTemplate.fill(entry.content, values.mapValues { quote(it.value) })
        return if (entry.runIn == RunMode.DEBIAN) wrapForDebian(inner) else inner
    }
}
