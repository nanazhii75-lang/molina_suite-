package com.molina.suite.feature.mpv

import android.util.Log
import com.molina.suite.mpv.search.YoutubeResult
import com.molina.suite.mpv.search.YoutubeSearchException
import com.molina.suite.mpv.search.YoutubeSearchException.Reason
import com.molina.suite.mpv.search.YoutubeSearchSource
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Pencarian YouTube lewat pembungkus molina-ytdlp (yt-dlp di dalam Debian/proot-distro).
 * Dijalankan dengan lingkungan kosong, setara `env -i`, karena pembungkus mengatur
 * PATH/HOME/TMPDIR sendiri.
 */
internal class YtdlpSearchSource(private val wrapper: File) : YoutubeSearchSource {

    override fun search(query: String, limit: Int): List<YoutubeResult> {
        val cleaned = query.filter { !it.isISOControl() }.trim()
        if (cleaned.isEmpty()) return emptyList()
        val count = limit.coerceIn(1, MAX_RESULTS)

        if (!wrapper.isFile || !wrapper.canExecute()) {
            throw YoutubeSearchException(Reason.UNAVAILABLE, "Pembungkus molina-ytdlp belum terpasang: " + wrapper.path)
        }

        val command = listOf(
            wrapper.absolutePath,
            "--flat-playlist",
            "--dump-json",
            "--no-warnings",
            "--ignore-errors",
            "ytsearch${count + EXTRA_FETCH}:$cleaned"
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
        val stdout = StringBuffer()
        val stderr = StringBuffer()
        val outReader = drain(process.inputStream, stdout, "ytdl-search-out")
        val errReader = drain(process.errorStream, stderr, "ytdl-search-err")

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

        val parsed = YtdlpOutputParser.parse(stdout.toString(), count)
        val exit = process.exitValue()
        if (parsed.isEmpty() && exit != 0) {
            val detail = tail(stderr.toString())
            Log.w(TAG, "yt-dlp keluar dengan kode $exit: $detail")
            throw YoutubeSearchException(Reason.FAILED, "yt-dlp gagal (kode $exit): $detail")
        }
        return parsed
    }

    private fun drain(stream: InputStream, sink: StringBuffer, name: String): Thread =
        Thread({
            try {
                stream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val buffer = CharArray(8192)
                    while (sink.length < MAX_CHARS) {
                        val read = reader.read(buffer)
                        if (read < 0) break
                        sink.append(buffer, 0, read)
                    }
                }
            } catch (ignored: IOException) {
                // proses dihentikan atau pipa ditutup
            }
        }, name).apply {
            isDaemon = true
            start()
        }

    private fun tail(text: String): String =
        text.trim().lines().takeLast(3).joinToString(" | ").take(300)

    private companion object {
        const val TAG = "YtdlpSearchSource"
        const val TIMEOUT_SECONDS = 60L
        const val JOIN_MILLIS = 2000L
        const val MAX_RESULTS = 20
        const val MAX_CHARS = 4_000_000
        // Cadangan untuk entri non-video (mis. Mix) yang dibuang parser.
        const val EXTRA_FETCH = 5
    }
}
