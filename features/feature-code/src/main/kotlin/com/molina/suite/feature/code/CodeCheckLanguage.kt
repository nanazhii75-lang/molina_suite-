package com.molina.suite.feature.code

import com.molina.suite.core.common.ScriptRunLine
import java.util.Locale

/**
 * Bahasa yang bisa diperiksa lewat Lakukan. Pemeriksaan hanya memeriksa sintaks atau kompilasi
 * dan tidak pernah menjalankan program: hasil kompilasi ditulis ke folder sementara yang dibuang
 * saat selesai. Id alat mengikuti katalog menu Paket; alat bawaan Debian (bash) tidak punya id.
 */
internal enum class CodeCheckLanguage(
    val label: String,
    /** Id katalog Paket yang harus terpasang; kosong bila alatnya bawaan Debian. */
    val toolIds: List<String>,
    /** Id katalog yang versinya ditampilkan di dropdown Kompilator; null bila tidak ada. */
    val versionToolId: String?,
    /** Batas waktu pemeriksaan; kompilator berbasis JVM butuh lebih lama di perangkat lambat. */
    val timeoutSeconds: Int,
    private val extensions: Set<String>
) {
    PYTHON("Python", listOf("python"), "python", 60, setOf("py")),
    BASH("Bash", emptyList(), null, 60, setOf("sh", "bash")),
    NODE("Node.js", listOf("node"), "node", 60, setOf("js", "mjs", "cjs")),
    JAVA("Java", listOf("jdk17"), "jdk17", 240, setOf("java")),
    KOTLIN("Kotlin", listOf("jdk17", "kotlin"), "kotlin", 240, setOf("kt")),
    C("C", listOf("gcc"), "gcc", 120, setOf("c")),
    CPP("C++", listOf("gcc"), "gcc", 120, setOf("cpp", "cc", "cxx")),
    GO("Go", listOf("go"), "go", 120, setOf("go")),
    RUST("Rust", listOf("rust"), "rust", 180, setOf("rs")),
    PHP("PHP", listOf("php"), "php", 60, setOf("php")),
    RUBY("Ruby", listOf("ruby"), "ruby", 60, setOf("rb")),
    LUA("Lua", listOf("lua"), "lua", 60, setOf("lua")),
    JSON("JSON", listOf("jq"), "jq", 60, setOf("json"));

    /** Perintah pemeriksa untuk berkas bernama [file] (sudah di-quote); `$T` adalah folder sementara. */
    private fun command(file: String): String = when (this) {
        PYTHON -> "python3 -c 'import sys; compile(open(sys.argv[1], \"rb\").read(), sys.argv[1], \"exec\")' $file"
        BASH -> "bash -n $file"
        NODE -> "node --check $file"
        JAVA -> "javac -d \"\$T\" $file"
        KOTLIN -> "kotlinc $file -d \"\$T\""
        C -> "gcc -fsyntax-only -Wall $file"
        CPP -> "g++ -fsyntax-only -Wall $file"
        GO -> "go build -o /dev/null $file"
        RUST -> "rustc --edition 2021 --emit=metadata -o \"\$T/check.rmeta\" $file"
        PHP -> "php -l $file"
        RUBY -> "ruby -c $file"
        LUA -> "luac5.4 -p $file"
        JSON -> "jq empty $file"
    }

    /**
     * Skrip bash (satu baris) yang memeriksa berkas di [path] dari foldernya sendiri, dibatasi
     * [timeoutSeconds]. Folder sementara `$T` dibuang lewat trap saat skrip berakhir, dan kode
     * keluar skrip adalah kode keluar pemeriksa (124 bila kena batas waktu). Mengembalikan null
     * bila jalur tidak valid (kosong atau mengandung baris baru).
     */
    fun checkScript(path: String): String? {
        if (path.any { it == '\n' || it == '\r' }) return null
        val normalized = ScriptRunLine.normalizePath(path)
        val name = normalized.substringAfterLast('/')
        if (name.isEmpty()) return null
        val dir = normalized.substringBeforeLast('/', "").ifEmpty { "/" }
        val argument = ScriptRunLine.quote(if (name.startsWith("-")) "./$name" else name)
        return "T=\"\$(mktemp -d)\" && trap 'rm -rf \"\$T\"' EXIT && cd " + ScriptRunLine.quote(dir) +
            " && timeout $timeoutSeconds " + command(argument)
    }

    companion object {
        private val BY_EXTENSION: Map<String, CodeCheckLanguage> =
            values().flatMap { language -> language.extensions.map { it to language } }.toMap()

        /** Bahasa pemeriksa untuk [fileName]; null bila ekstensinya tidak bisa diperiksa. */
        fun of(fileName: String): CodeCheckLanguage? {
            if (!fileName.contains('.')) return null
            return BY_EXTENSION[fileName.substringAfterLast('.').lowercase(Locale.ROOT)]
        }
    }
}
