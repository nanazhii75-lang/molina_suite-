package com.molina.suite.feature.code

import com.molina.suite.core.common.ScriptRunLine
import com.molina.suite.core.common.ScriptRunTarget

/**
 * Perintah jalankan untuk bahasa di luar .sh, .bash, dan .py (yang dipegang [ScriptRunLine]).
 * Bahasa dan id alatnya mengikuti [CodeCheckLanguage], jadi katalog Paket tetap satu sumber.
 * Program yang dikompilasi ditulis ke folder sementara di dalam Debian yang dibuang saat
 * perintah selesai. Hanya menyusun teks; tidak menjalankan apa pun.
 */
internal object CodeRunCommand {

    private val RUNNABLE = setOf(
        CodeCheckLanguage.NODE,
        CodeCheckLanguage.PHP,
        CodeCheckLanguage.RUBY,
        CodeCheckLanguage.LUA,
        CodeCheckLanguage.JAVA,
        CodeCheckLanguage.KOTLIN,
        CodeCheckLanguage.C,
        CodeCheckLanguage.CPP,
        CodeCheckLanguage.GO,
        CodeCheckLanguage.RUST
    )

    private const val TEMP_DIR = "T=\"\$(mktemp -d)\" && trap 'rm -rf \"\$T\"' EXIT && "

    /** True bila [fileName] bisa dijalankan, lewat [ScriptRunLine] (sh, bash, py) atau bahasa di sini. */
    fun isRunnable(fileName: String): Boolean =
        ScriptRunLine.isRunnableName(fileName) || languageOf(fileName) != null

    /** Bahasa tambahan yang bisa dijalankan untuk [fileName]; null bila bukan salah satunya. */
    fun languageOf(fileName: String): CodeCheckLanguage? =
        CodeCheckLanguage.of(fileName)?.takeIf { it in RUNNABLE }

    /**
     * Baris ketik untuk menjalankan [path] dari foldernya sendiri. Berkas ber-shebang, .sh, .bash,
     * dan .py diserahkan ke [ScriptRunLine.forFile]; bahasa lain hanya untuk [ScriptRunTarget.DEBIAN]
     * karena kompilator dan interpreternya dipasang lewat menu Paket di Debian. Null bila tidak
     * bisa dijalankan atau jalurnya tidak valid.
     */
    fun lineFor(path: String, head: String, target: ScriptRunTarget): String? {
        ScriptRunLine.forFile(path, head, target)?.let { return it }
        if (target != ScriptRunTarget.DEBIAN) return null
        if (path.any { it == '\n' || it == '\r' }) return null
        val normalized = ScriptRunLine.normalizePath(path)
        val name = normalized.substringAfterLast('/')
        if (name.isEmpty()) return null
        val language = languageOf(name) ?: return null
        val dir = normalized.substringBeforeLast('/', "").ifEmpty { "/" }
        val argument = ScriptRunLine.quote(if (name.startsWith("-")) "./$name" else name)
        val command = command(language, argument) ?: return null
        return ScriptRunLine.wrapForDebian("cd " + ScriptRunLine.quote(dir) + " && " + command)
    }

    private fun command(language: CodeCheckLanguage, file: String): String? = when (language) {
        CodeCheckLanguage.NODE -> "node $file"
        CodeCheckLanguage.PHP -> "php $file"
        CodeCheckLanguage.RUBY -> "ruby $file"
        CodeCheckLanguage.LUA -> "lua5.4 $file"
        CodeCheckLanguage.JAVA -> "java $file"
        CodeCheckLanguage.GO -> "go run $file"
        CodeCheckLanguage.KOTLIN ->
            TEMP_DIR + "kotlinc $file -include-runtime -d \"\$T/app.jar\" && java -jar \"\$T/app.jar\""
        CodeCheckLanguage.C -> TEMP_DIR + "gcc $file -o \"\$T/app\" && \"\$T/app\""
        CodeCheckLanguage.CPP -> TEMP_DIR + "g++ $file -o \"\$T/app\" && \"\$T/app\""
        CodeCheckLanguage.RUST ->
            TEMP_DIR + "rustc --edition 2021 $file -o \"\$T/app\" && \"\$T/app\""
        else -> null
    }
}
