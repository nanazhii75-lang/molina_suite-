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

    /**
     * Uji shell: benar bila shell berjalan di dalam Debian (PRoot). Android host tidak punya
     * berkas ini. Jangan memakai `command -v proot-distro`: perintah itu tetap ditemukan dari
     * dalam Debian dan proot-distro menolak jalan di bawah PRoot.
     */
    const val IN_DEBIAN_TEST = "[ -f /etc/debian_version ]"

    /** Membungkus [value] dengan tanda kutip tunggal shell. */
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /**
     * Menjalankan [inner] di dalam Debian. Dari shell host dipakai proot-distro; bila sudah
     * berada di dalam Debian (terdeteksi lewat /etc/debian_version) perintah dijalankan langsung.
     */
    fun wrapForDebian(inner: String): String {
        val quoted = quote(inner)
        return "if $IN_DEBIAN_TEST; then bash -c $quoted; " +
            "elif command -v proot-distro >/dev/null 2>&1; then " +
            "proot-distro login debian -- bash -c $quoted; " +
            "else echo 'Debian belum terpasang. Pasang dulu lewat Settings, bagian Debian.'; fi"
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

    /**
     * Isi skrip untuk entri Library. Library menjalankan skrip multi-baris dengan bash kecuali
     * ada shebang, jadi skrip Python tanpa shebang diberi baris #!/usr/bin/env python3 supaya
     * tetap dijalankan python3. Skrip lain dikembalikan apa adanya.
     */
    fun withLibraryShebang(fileName: String?, text: String): String {
        if (fileName == null || hasShebang(text)) return text
        if (interpreterFor(fileName) != "python3") return text
        return "#!/usr/bin/env python3\n" + text.trimStart('\uFEFF')
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
