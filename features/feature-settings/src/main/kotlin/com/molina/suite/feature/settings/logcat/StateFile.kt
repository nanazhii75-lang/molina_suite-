package com.molina.suite.feature.settings.logcat

import java.io.File
import java.io.IOException

/**
 * Berkas status kecil berisi satu teks pendek. Penulisan lewat berkas sementara lalu diganti,
 * jadi pembaca tidak pernah menemukan isi yang setengah tertulis meski proses mati di tengah menulis.
 */
internal object StateFile {

    private const val TEMP_SUFFIX = ".tmp"

    /** Isi [file], atau null bila belum ada atau tidak terbaca. */
    fun read(file: File): String? = try {
        if (file.isFile) file.readText(Charsets.UTF_8) else null
    } catch (e: IOException) {
        null
    }

    /** Menulis [text] ke [file]; false bila folder atau berkasnya tidak bisa ditulis. */
    fun write(file: File, text: String): Boolean {
        return try {
            val dir = file.parentFile
            if (dir != null && !dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) return false
            val temp = File(dir, file.name + TEMP_SUFFIX)
            temp.writeText(text, Charsets.UTF_8)
            if (!temp.renameTo(file)) {
                file.writeText(text, Charsets.UTF_8)
                temp.delete()
            }
            true
        } catch (e: IOException) {
            false
        }
    }
}
