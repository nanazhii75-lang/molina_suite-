package com.molina.suite.feature.settings.logcat

import java.io.File
import java.io.IOException
import java.util.Date

/** Menyimpan, membaca, dan memangkas laporan crash di satu folder; hanya berkas "crash-*.txt" yang disentuh. */
internal object CrashLogStore {

    const val KEEP = 20

    /** Laporan di [dir], yang terbaru lebih dulu. */
    fun list(dir: File): List<File> =
        (dir.listFiles { file -> file.isFile && CrashReport.isCrashFile(file.name) } ?: emptyArray())
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })

    fun latest(dir: File): File? = list(dir).firstOrNull()

    /** Menulis [content] sebagai laporan baru lalu memangkas yang lama; null bila gagal menulis. */
    fun write(dir: File, time: Date, content: String): File? {
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) return null
        var suffix = 1
        var file = File(dir, CrashReport.fileName(time))
        while (file.exists()) {
            suffix++
            file = File(dir, CrashReport.fileName(time, suffix))
        }
        return try {
            file.writeText(content, Charsets.UTF_8)
            prune(dir, KEEP)
            file
        } catch (e: IOException) {
            null
        }
    }

    /** Menghapus laporan terlama sampai tersisa [keep]. */
    fun prune(dir: File, keep: Int) {
        list(dir).drop(keep).forEach { it.delete() }
    }

    /** Isi laporan; null bila tidak terbaca. */
    fun read(file: File): String? = try {
        file.readText(Charsets.UTF_8)
    } catch (e: IOException) {
        null
    }
}
