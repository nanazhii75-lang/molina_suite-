package com.molina.suite.feature.settings.mpvconfig

import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Berkas konfigurasi mpv di folder konfigurasi pemutar (folder files aplikasi): mpv.conf,
 * input.conf, dan skrip Lua di scripts/. mpv membacanya saat pemutar dibuka, jadi perubahan
 * berlaku pada pemutaran berikutnya.
 */
internal object MpvConfigFiles {

    const val MPV_CONF = "mpv.conf"
    const val INPUT_CONF = "input.conf"
    const val SCRIPTS_DIR = "scripts"
    const val SCRIPT_EXTENSION = ".lua"
    const val MAX_NAME_LENGTH = 48

    private val NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

    sealed class NameCheck {
        class Valid(val fileName: String) : NameCheck()
        object Empty : NameCheck()
        object TooLong : NameCheck()
        object Illegal : NameCheck()
    }

    sealed class Ensure {
        class Ready(val file: File, val created: Boolean) : Ensure()
        class Failed(val reason: String) : Ensure()
    }

    fun scriptsDir(configDir: File): File = File(configDir, SCRIPTS_DIR)

    /** Memeriksa nama skrip dari isian pengguna; ".lua" ditambahkan bila belum ada. */
    fun checkScriptName(input: String): NameCheck {
        var base = input.trim()
        if (base.endsWith(SCRIPT_EXTENSION, ignoreCase = true)) {
            base = base.dropLast(SCRIPT_EXTENSION.length)
        }
        if (base.isEmpty()) return NameCheck.Empty
        if (base.length > MAX_NAME_LENGTH) return NameCheck.TooLong
        if (!NAME.matches(base)) return NameCheck.Illegal
        return NameCheck.Valid(base + SCRIPT_EXTENSION)
    }

    /** Skrip .lua tingkat atas di scripts/, urut nama. */
    fun listScripts(configDir: File): List<File> =
        scriptsDir(configDir)
            .listFiles { file -> file.isFile && file.name.endsWith(SCRIPT_EXTENSION, ignoreCase = true) }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            ?: emptyList()

    /**
     * Memastikan [file] ada (folder induknya dibuat bila perlu). Berkas yang sudah ada tidak
     * disentuh; [initialText] hanya ditulis ke berkas yang baru dibuat.
     */
    fun ensureFile(file: File, initialText: String? = null): Ensure {
        if (file.isDirectory) return Ensure.Failed("sudah ada folder dengan nama ini")
        return try {
            val parent = file.parentFile
            if (parent != null && !parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
                return Ensure.Failed("folder " + parent.name + " tidak bisa dibuat")
            }
            val created = file.createNewFile()
            if (created && initialText != null) file.writeText(initialText)
            Ensure.Ready(file, created)
        } catch (e: IOException) {
            Ensure.Failed(e.message ?: e.javaClass.simpleName)
        } catch (e: SecurityException) {
            Ensure.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Isi awal skrip baru: hanya penanda nama, tanpa kode. */
    fun starterText(fileName: String): String =
        "-- " + fileName + ": skrip mpv Molina, dimuat otomatis setiap mpv dibuka.\n"
}
