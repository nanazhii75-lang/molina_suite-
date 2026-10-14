package com.molina.suite.feature.settings.logcat

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Locale

/**
 * Riwayat log di disk: deretan berkas "history-NNNNNN.log" (satu baris log per baris teks, format
 * [LogcatEntry.toLine]) di satu folder. Berkas aktif adalah yang bernomor tertinggi; bila
 * melewati [maxSegmentBytes] dibuka berkas baru, dan berkas tertua dibuang sampai tersisa paling
 * banyak [maxSegments]. Total ukuran jadi terbatas (sekitar [maxSegmentBytes] x [maxSegments]).
 * Tidak mengenal Android: folder diberikan pemanggil. Aman dipakai dari beberapa thread.
 */
internal class LogFileStore(
    private val dir: File,
    private val maxSegmentBytes: Long = DEFAULT_SEGMENT_BYTES,
    private val maxSegments: Int = DEFAULT_SEGMENTS
) {

    private val lock = Any()

    init {
        require(maxSegmentBytes > 0) { "maxSegmentBytes harus positif" }
        require(maxSegments > 0) { "maxSegments harus positif" }
    }

    /** Menambahkan [lines] ke berkas aktif; false bila folder atau berkas tidak bisa ditulis. */
    fun append(lines: List<String>): Boolean {
        if (lines.isEmpty()) return true
        synchronized(lock) {
            return try {
                if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) return false
                writeLines(lines)
                prune()
                true
            } catch (e: IOException) {
                false
            }
        }
    }

    /** Sampai [maxLines] baris terakhir, urut dari tertua; berkas yang tidak terbaca dilewati. */
    fun readTail(maxLines: Int): List<String> {
        if (maxLines <= 0) return emptyList()
        synchronized(lock) {
            val chunks = ArrayDeque<List<String>>()
            var total = 0
            for (segment in segments().asReversed()) {
                val lines = readLines(segment)
                chunks.addFirst(lines)
                total += lines.size
                if (total >= maxLines) break
            }
            return chunks.flatten().takeLast(maxLines)
        }
    }

    /** Menghapus seluruh riwayat; false bila ada berkas yang tidak bisa dihapus. */
    fun clear(): Boolean = synchronized(lock) {
        var allDeleted = true
        for (segment in segments()) {
            if (!segment.delete() && segment.exists()) allDeleted = false
        }
        allDeleted
    }

    private fun writeLines(lines: List<String>) {
        var segment = segments().lastOrNull() ?: segmentFile(FIRST_SEQUENCE)
        var out = open(segment)
        var size = segment.length()
        try {
            for (line in lines) {
                val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
                if (size > 0 && size + bytes.size > maxSegmentBytes) {
                    out.close()
                    segment = segmentFile((sequenceNumber(segment.name) ?: FIRST_SEQUENCE) + 1)
                    out = open(segment)
                    size = 0
                }
                out.write(bytes)
                size += bytes.size
            }
        } finally {
            out.close()
        }
    }

    /**
     * Membuka [file] untuk ditambah. Bila berkas berakhir tanpa pemisah baris (proses mati di
     * tengah menulis), pemisah ditambahkan dulu supaya baris baru tidak menempel ke sisa lama.
     */
    private fun open(file: File): BufferedOutputStream {
        val needsBreak = file.length() > 0 && !endsWithNewline(file)
        val out = BufferedOutputStream(FileOutputStream(file, true))
        if (needsBreak) out.write('\n'.code)
        return out
    }

    private fun endsWithNewline(file: File): Boolean = try {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() == 0L) {
                true
            } else {
                raf.seek(raf.length() - 1)
                raf.read() == '\n'.code
            }
        }
    } catch (e: IOException) {
        true
    }

    private fun prune() {
        segments().dropLast(maxSegments).forEach { it.delete() }
    }

    private fun readLines(file: File): List<String> = try {
        file.readLines(Charsets.UTF_8).filter { it.isNotEmpty() }
    } catch (e: IOException) {
        emptyList()
    }

    /** Berkas riwayat di folder, urut dari nomor terkecil; berkas lain di folder itu tidak disentuh. */
    private fun segments(): List<File> {
        val files = dir.listFiles() ?: return emptyList()
        return files
            .filter { it.isFile }
            .mapNotNull { file -> sequenceNumber(file.name)?.let { file to it } }
            .sortedBy { it.second }
            .map { it.first }
    }

    private fun segmentFile(sequence: Int): File =
        File(dir, String.format(Locale.ROOT, "history-%06d.log", sequence))

    private fun sequenceNumber(name: String): Int? =
        SEGMENT_NAME.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()

    companion object {
        const val DEFAULT_SEGMENT_BYTES = 1024L * 1024L
        const val DEFAULT_SEGMENTS = 5
        private const val FIRST_SEQUENCE = 1
        private val SEGMENT_NAME = Regex("""history-(\d{6,})\.log""")
    }
}
