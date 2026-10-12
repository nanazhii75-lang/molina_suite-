package com.molina.suite.feature.code

import java.util.Locale

/**
 * Ekstensi tab Code: saran saat nama berkas diketik, pilihan dropdown di Simpan sebagai, dan
 * batas jenis berkas yang boleh dibuka. Berkas tanpa ekstensi (Makefile, LICENSE) dianggap teks
 * biasa dan boleh dibuka; berkas dengan ekstensi di luar daftar ditolak.
 */
internal object CodeExtensions {

    private const val DEFAULT_LIMIT = 10

    /**
     * Ekstensi umum, juga isi dropdown Simpan sebagai. Urutan daftar adalah urutan prioritas:
     * ekstensi yang lebih umum muncul lebih dulu.
     */
    private val COMMON = listOf(
        "txt", "md", "json", "xml", "html", "css", "js", "ts", "kt", "java", "py", "sh",
        "c", "cpp", "h", "cs", "go", "rs", "php", "rb", "swift", "dart", "lua", "sql",
        "yaml", "yml", "toml", "ini", "csv", "tsv", "log", "kts", "gradle", "jsx", "tsx", "vue",
        "htm", "markdown", "jsonc", "mjs", "cjs", "mts", "cts", "bash", "zsh", "ksh", "bat", "ps1",
        "pyw", "cc", "cxx", "hpp", "hh", "hxx", "ino", "cfg", "conf", "env", "lock", "svg",
        "gitignore", "editorconfig", "bashrc", "profile", "properties"
    )

    /**
     * Ekstensi yang bisa dibuka, per kategori. Nama ganda seperti .blade.php, .test.js, dan
     * .spec.ts sudah tercakup oleh ekstensi terakhirnya (php, js, ts). .class sengaja tidak ada
     * karena berkas biner.
     */
    private val SUPPORTED_GROUPS = arrayOf(
        // Code dan Web
        "html htm xhtml css scss sass less js mjs cjs jsx ts tsx vue svelte astro json jsonc svg xml",
        // Bahasa pemrograman
        "c h cpp cc cxx hpp java kt kts go rs py pyw php phtml rb pl pm cs fs fsx vb swift m mm " +
            "dart lua r scala sc groovy clj cljs cljc ex exs erl hrl hs lhs elm nim zig d pas pp " +
            "f f90 f95 for cob cbl asm s",
        // Scripting dan shell
        "sh bash zsh fish bat cmd ps1 psm1 awk sed tcl",
        // Database dan API
        "sql prisma graphql gql proto http rest openapi swagger",
        // Dokumen dan teks
        "txt text md markdown mdx rst adoc asciidoc org tex bib rtf",
        // Data dan konfigurasi
        "yaml yml toml ini cfg conf properties env csv tsv jsonl ndjson",
        // Build dan pengembangan
        "gradle kts mk make cmake mod sum lock tf hcl dockerignore gitignore gitattributes editorconfig",
        // Template
        "ejs hbs handlebars pug njk twig liquid mustache erb haml slim",
        // Testing
        "feature",
        // Notebook dan log
        "ipynb log stacktrace"
    )

    /** Gabungan daftar kategori dan [COMMON], supaya semua pilihan dropdown bisa dibuka lagi. */
    private val OPENABLE: Set<String> = buildSet {
        for (group in SUPPORTED_GROUPS) {
            for (extension in group.split(' ')) {
                if (extension.isNotEmpty()) add(extension.lowercase(Locale.ROOT))
            }
        }
        addAll(COMMON)
    }

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

    /** Isi dropdown ekstensi di dialog Simpan sebagai. */
    fun saveAsChoices(): List<String> = COMMON

    /** Bagian setelah titik terakhir pada [fileName]; null bila tidak ada titik atau titik di akhir. */
    fun extensionOf(fileName: String): String? {
        val dot = fileName.lastIndexOf('.')
        if (dot < 0 || dot == fileName.length - 1) return null
        return fileName.substring(dot + 1)
    }

    /** True bila berkas tanpa ekstensi atau ekstensinya ada di daftar yang didukung. */
    fun isOpenable(fileName: String): Boolean {
        val extension = extensionOf(fileName) ?: return true
        return extension.lowercase(Locale.ROOT) in OPENABLE
    }
}
