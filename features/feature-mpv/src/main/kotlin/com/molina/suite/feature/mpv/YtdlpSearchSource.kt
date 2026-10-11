package com.molina.suite.feature.mpv

import android.util.Log
import com.molina.suite.mpv.search.YoutubeResult
import com.molina.suite.mpv.search.YoutubeSearchException
import com.molina.suite.mpv.search.YoutubeSearchException.Reason
import com.molina.suite.mpv.search.YoutubeSearchSource
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Pencarian YouTube lewat pembungkus molina-ytdlp (yt-dlp di dalam Debian/proot-distro).
 * Dijalankan dengan lingkungan kosong, setara `env -i`, karena pembungkus mengatur
 * PATH/HOME/TMPDIR sendiri. Memakai metadata penuh agar waktu unggah tersedia; yt-dlp
 * mencetak satu JSON per video begitu selesai, jadi progres bisa dilaporkan.
 */
internal class YtdlpSearchSource(private val wrapper: File) : YoutubeSearchSource {

    override fun search(
        query: String,
        offset: Int,
        limit: Int,
        onProgress: (Int) -> Unit
    ): List<YoutubeResult> {
        val cleaned = query.filter { !it.isISOControl() }.trim()
        if (cleaned.isEmpty()) return emptyList()
        val start = offset.coerceAtLeast(0)
        val count = limit.coerceIn(1, MAX_RESULTS)
        val end = (start + count).coerceAtMost(MAX_TOTAL)
        if (end <= start) return emptyList()

        if (!wrapper.isFile || !wrapper.canExecute()) {
            throw YoutubeSearchException(Reason.UNAVAILABLE, "Pembungkus molina-ytdlp belum terpasang: " + wrapper.path)
        }

        val encoded = URLEncoder.encode(cleaned, "UTF-8")
        val command = listOf(
            wrapper.absolutePath,
            "--dump-json",
            "--no-warnings",
            "--ignore-errors",
            // Kunci ytsearchdate sudah dihapus dari yt-dlp. URL hasil pencarian YouTube dengan
            // parameter sp (urut tanggal unggah, hanya video) tetap didukung; YouTube sendiri
            // mengurutkan terbaru dulu. --playlist-items hanya mengambil metadata penuh untuk
            // halaman yang diminta.
            "--playlist-items",
            "${start + 1}-$end",
            "https://www.youtube.com/results?search_query=$encoded&sp=$SORT_BY_DATE_VIDEOS"
        )
        val process = try {
            ProcessBuilder(command).also { it.environment().clear() }.start()
        } catch (e: IOException) {
            throw YoutubeSearchException(Reason.UNAVAILABLE, "Gagal menjalankan molina-ytdlp", e)
        }

        try {
            process.outputStream.close()
        } catch (ignored: IOException) {
            // stdin tidak dipakai
        }

        val results = ArrayList<YoutubeResult>()
        val stderr = StringBuffer()

        val outReader = startThread("ytdl-search-out") {
            try {
                process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        val item = YtdlpOutputParser.parseLine(line) ?: continue
                        val size = synchronized(results) {
                            results.add(item)
                            results.size
                        }
                        try {
                            onProgress(size)
                        } catch (e: RuntimeException) {
                            Log.w(TAG, "onProgress melempar pengecualian", e)
                        }
                    }
                }
            } catch (ignored: IOException) {
                // proses dihentikan atau pipa ditutup
            }
        }
        val errReader = startThread("ytdl-search-err") {
            try {
                process.errorStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val buffer = CharArray(4096)
                    while (true) {
                        val read = reader.read(buffer)
                        if (read < 0) break
                        // tetap dibaca habis agar pipa tidak penuh, tetapi simpan secukupnya
                        if (stderr.length < MAX_ERR_CHARS) stderr.append(buffer, 0, read)
                    }
                }
            } catch (ignored: IOException) {
                // proses dihentikan atau pipa ditutup
            }
        }

        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw YoutubeSearchException(Reason.TIMEOUT, "Pencarian melebihi $TIMEOUT_SECONDS detik")
            }
            outReader.join(JOIN_MILLIS)
            errReader.join(JOIN_MILLIS)
        } catch (e: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            throw YoutubeSearchException(Reason.FAILED, "Pencarian dibatalkan", e)
        }

        val collected = synchronized(results) { ArrayList(results) }
        val finished = YtdlpOutputParser.finish(collected, count)
        val exit = process.exitValue()
        if (finished.isEmpty() && exit != 0) {
            val detail = tail(stderr.toString())
            Log.w(TAG, "yt-dlp keluar dengan kode $exit: $detail")
            throw YoutubeSearchException(Reason.FAILED, "yt-dlp gagal (kode $exit): $detail")
        }
        return finished
    }

    private fun startThread(name: String, block: () -> Unit): Thread =
        Thread({ block() }, name).apply {
            isDaemon = true
            start()
        }

    private fun tail(text: String): String =
        text.trim().lines().takeLast(3).joinToString(" | ").take(300)

    private companion object {
        const val TAG = "YtdlpSearchSource"
        const val TIMEOUT_SECONDS = 120L
        const val JOIN_MILLIS = 2000L
        const val MAX_RESULTS = 20
        const val MAX_TOTAL = 60
        const val SORT_BY_DATE_VIDEOS = "CAISAhAB"
        const val MAX_ERR_CHARS = 20_000
    }
}
