package com.molina.suite.core.common

/** Lingkungan tujuan menjalankan sebuah skrip lewat Terminal. */
enum class ScriptRunTarget {
    /** Di dalam Debian (proot-distro); bila terminal sudah di dalam Debian, dijalankan langsung. */
    DEBIAN,

    /** Di shell host (Molina Terminal) apa adanya. */
    HOST
}

/**
 * Menyusun satu baris ketik yang menjalankan berkas skrip lewat Terminal. Dipakai bersama oleh
 * Library (Settings) dan tab Code supaya logika Debian/host, salin ke HOME, dan chmod +x hanya
 * ada di satu tempat. Penyimpanan bersama (/sdcard) tidak bisa dieksekusi, jadi skrip
 * ber-shebang disalin dulu ke $HOME/.molina/run milik lingkungan tujuan lalu dijalankan dari sana.
 */
object ScriptRunLine {

    private const val SHARED_STORAGE_PREFIX = "/storage/emulated/0/"

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

    /** True bila baris pertama skrip adalah shebang (#!), mis. #!/usr/bin/env python3. */
    fun hasShebang(content: String): Boolean = content.trimStart('\uFEFF').startsWith("#!")

    /**
     * Perintah yang menyalin [scriptPath] ke $HOME/.molina/run, memberi izin execute, lalu
     * menjalankannya sehingga interpreter dari shebang yang dipakai. $HOME ikut lingkungan tujuan.
     */
    fun executeCopy(scriptPath: String): String {
        val name = scriptPath.substringAfterLast('/')
        val target = "\"\$HOME/.molina/run/" + escapeDoubleQuoted(name) + "\""
        return "mkdir -p \"\$HOME/.molina/run\" && cp " + quote(scriptPath) + " " + target +
            " && chmod +x " + target + " && " + target
    }

    /** Dari Debian penyimpanan bersama terlihat di /sdcard; dari host kedua nama jalur sama. */
    fun normalizePath(path: String): String =
        if (path.startsWith(SHARED_STORAGE_PREFIX)) {
            "/sdcard/" + path.removePrefix(SHARED_STORAGE_PREFIX)
        } else {
            path
        }

    /** True bila nama berkas dikenali dari ekstensinya (.sh, .bash, .py). */
    fun isRunnableName(fileName: String): Boolean = interpreterFor(fileName) != null

    /**
     * Baris ketik untuk menjalankan berkas di [path] dari foldernya sendiri. [head] adalah awal
     * isi berkas, dipakai untuk mendeteksi shebang. Berkas ber-shebang dijalankan sebagai
     * executable; selain itu interpreter dipilih dari ekstensi. Mengembalikan null bila berkas
     * tidak bisa dijalankan (tanpa shebang dan ekstensi tidak dikenal, atau jalur tidak valid).
     */
    fun forFile(path: String, head: String, target: ScriptRunTarget): String? {
        if (path.any { it == '\n' || it == '\r' }) return null
        val normalized = normalizePath(path)
        val dir = normalized.substringBeforeLast('/', "").ifEmpty { "/" }
        val command = if (hasShebang(head)) {
            executeCopy(normalized)
        } else {
            val interpreter = interpreterFor(normalized.substringAfterLast('/')) ?: return null
            interpreter + " " + quote(normalized)
        }
        val inner = "cd " + quote(dir) + " && " + command
        return when (target) {
            ScriptRunTarget.DEBIAN -> wrapForDebian(inner)
            ScriptRunTarget.HOST -> inner
        }
    }

    private fun interpreterFor(fileName: String): String? =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "sh", "bash" -> "bash"
            "py" -> "python3"
            else -> null
        }

    private fun escapeDoubleQuoted(value: String): String {
        val out = StringBuilder(value.length + 8)
        for (ch in value) {
            if (ch == '\\' || ch == '"' || ch == '$' || ch == '`') out.append('\\')
            out.append(ch)
        }
        return out.toString()
    }
}
