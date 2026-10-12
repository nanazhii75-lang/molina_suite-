package com.molina.suite.feature.code

import java.util.Locale

/**
 * Ekstensi umum untuk saran saat nama berkas diketik di dialog Simpan sebagai. Urutan daftar
 * adalah urutan prioritas: ekstensi yang lebih umum muncul lebih dulu.
 */
internal object CodeExtensions {

    private const val DEFAULT_LIMIT = 10

    private val COMMON = listOf(
        "txt", "md", "json", "xml", "html", "css", "js", "ts", "kt", "java", "py", "sh",
        "c", "cpp", "h", "cs", "go", "rs", "php", "rb", "swift", "dart", "lua", "sql",
        "yaml", "yml", "toml", "ini", "csv", "tsv", "log", "kts", "gradle", "jsx", "tsx", "vue",
        "htm", "markdown", "jsonc", "mjs", "cjs", "mts", "cts", "bash", "zsh", "ksh", "bat", "ps1",
        "pyw", "cc", "cxx", "hpp", "hh", "hxx", "ino", "cfg", "conf", "env", "lock", "svg",
        "gitignore", "editorconfig", "bashrc", "profile", "properties"
    )

    /**
     * Ekstensi yang diawali [prefix] (tanpa titik, huruf besar-kecil diabaikan), paling banyak
     * [limit]. Ekstensi yang sama persis dengan [prefix] tidak ikut disarankan. Kosong bila
     * [prefix] kosong atau memuat karakter selain huruf dan angka.
     */
    fun suggest(prefix: String, limit: Int = DEFAULT_LIMIT): List<String> {
        if (prefix.isEmpty() || !prefix.all { it.isLetterOrDigit() }) return emptyList()
        val lowered = prefix.lowercase(Locale.ROOT)
        return COMMON.filter { it.startsWith(lowered) && it != lowered }.take(limit)
    }
}
