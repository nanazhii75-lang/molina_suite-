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

    /**
     * Seperti [wrapForDebian] untuk perintah yang butuh root (apt, pasang paket). Dari shell host
     * dipakai proot-distro (root); di dalam Debian sebagai root dijalankan langsung; di dalam
     * Debian sebagai user dicetak petunjuk karena apt akan ditolak.
     */
    fun wrapForDebianRoot(inner: String): String {
        val quoted = quote(inner)
        return "if command -v proot-distro >/dev/null 2>&1; then " +
            "proot-distro login debian -- bash -c $quoted; " +
            "elif [ \"$(id -u)\" = 0 ]; then bash -c $quoted; " +
            "else echo 'Perintah ini butuh root. Ketik exit, lalu molina-login root.'; fi"
    }

    /** Perintah Android (am, pm) hanya ada di shell host; di dalam Debian tampilkan petunjuk. */
    fun guardAndroid(inner: String): String =
        "if command -v proot-distro >/dev/null 2>&1; then $inner; " +
            "else echo 'Perintah ini hanya jalan di shell biasa. Ketik exit untuk keluar dari Debian.'; fi"

    /** [values] diisikan ke placeholder dengan quote shell otomatis. */
    fun build(entry: LibraryEntry, values: Map<String, String>): String =
        wrap(entry.runIn, fillScript(entry, values))

    /** True bila isi entri lebih dari satu baris, sehingga dijalankan sebagai berkas skrip. */
    fun isScript(content: String): Boolean = content.any { it == '\n' || it == '\r' }

    /** Isi entri dengan placeholder terisi (quote shell otomatis), tanpa pembungkus. */
    fun fillScript(entry: LibraryEntry, values: Map<String, String>): String =
        PlaceholderTemplate.fill(entry.content, values.mapValues { quote(it.value) })

    /** True bila baris pertama skrip adalah shebang (#!), mis. #!/usr/bin/env python3. */
    fun hasShebang(content: String): Boolean = content.trimStart('\uFEFF').startsWith("#!")

    /**
     * Satu baris ketik yang menjalankan berkas skrip hasil [ScriptStager].
     *
     * Penyimpanan bersama (/sdcard) tidak bisa di-chmod dan tidak bisa dieksekusi, jadi skrip
     * ber-shebang disalin dulu ke $HOME/.molina/run milik lingkungan tujuan (host atau Debian),
     * diberi izin execute, lalu dijalankan langsung sehingga interpreter dari shebang dipakai.
     * Skrip tanpa shebang tetap dijalankan dengan bash.
     */
    fun buildScriptRun(entry: LibraryEntry, scriptPath: String): String {
        if (!hasShebang(entry.content)) return wrap(entry.runIn, "bash " + quote(scriptPath))
        val name = scriptPath.substringAfterLast('/')
        val target = "\"\$HOME/.molina/run/" + name + "\""
        val inner = "mkdir -p \"\$HOME/.molina/run\" && cp " + quote(scriptPath) + " " + target +
            " && chmod +x " + target + " && " + target
        return wrap(entry.runIn, inner)
    }

    /** Menyisipkan perintah install sebelum isi entri; gagal install menghentikan jalannya. */
    fun withInstall(content: String, install: String): String =
        if (isScript(content)) "$install || exit 1\n$content" else "$install && $content"

    private fun wrap(runIn: RunMode, inner: String): String = when (runIn) {
        RunMode.DEBIAN -> wrapForDebian(inner)
        RunMode.ANDROID -> guardAndroid(inner)
        RunMode.HOST -> inner
    }
}
