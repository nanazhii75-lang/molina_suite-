package com.molina.suite.feature.settings.logcat

import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Menulis baris logcat yang sedang tampil ke berkas teks (satu baris per entri, format yang sama
 * dengan [LogcatEntry.toLine]) dan menyusun teks salinan. Tidak mengenal Android: folder tujuan
 * diberikan pemanggil.
 */
internal object LogcatExporter {

    sealed interface Result {
        data class Saved(val file: File, val lines: Int) : Result
        data class Failed(val reason: String) : Result
    }

    private const val FILE_PREFIX = "logcat-"

    /** Jumlah ekspor terbaru yang dipertahankan di folder; yang lebih lama dihapus. */
    private const val KEEP = 20

    /** Nama berkas yang terurut menurut waktu, mis. "logcat-20261014-063000.txt". */
    fun fileName(time: Date, suffix: Int = 1): String =
        FILE_PREFIX + stamp(time) + (if (suffix > 1) "-$suffix" else "") + ".txt"

    /** Isi berkas: tiga baris keterangan berawalan '#', baris kosong, lalu semua entri. */
    fun text(entries: List<LogcatEntry>, time: Date): String {
        val header = "# Molina logcat\n# Diekspor: ${readable(time)}\n# Baris: ${entries.size}\n\n"
        return header + entries.joinToString("\n", postfix = "\n") { it.toLine() }
    }

    /** Teks [count] baris terakhir dari [entries] untuk disalin; kosong bila tidak ada entri. */
    fun tail(entries: List<LogcatEntry>, count: Int): String =
        entries.takeLast(count).joinToString("\n") { it.toLine() }

    /** Menyimpan [entries] ke folder [dir]; berkas yang sudah ada tidak pernah ditimpa. */
    fun save(entries: List<LogcatEntry>, dir: File, time: Date = Date()): Result {
        if (entries.isEmpty()) return Result.Failed("Tidak ada baris untuk disimpan.")
        return try {
            if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
                return Result.Failed("Folder ${dir.path} tidak bisa dibuat.")
            }
            var suffix = 1
            var file = File(dir, fileName(time))
            while (file.exists()) {
                suffix++
                file = File(dir, fileName(time, suffix))
            }
            file.writeText(text(entries, time), Charsets.UTF_8)
            prune(dir)
            Result.Saved(file, entries.size)
        } catch (e: IOException) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        } catch (e: SecurityException) {
            Result.Failed(e.message ?: "Izin ditolak.")
        }
    }

    private fun prune(dir: File) {
        val exports = dir.listFiles { f -> f.isFile && f.name.startsWith(FILE_PREFIX) && f.name.endsWith(".txt") }
            ?: return
        exports
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
            .drop(KEEP)
            .forEach { it.delete() }
    }

    private fun stamp(time: Date): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(time)

    private fun readable(time: Date): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(time)
}
