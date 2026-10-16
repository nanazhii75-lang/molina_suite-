package com.molina.suite.feature.mpv

import android.app.Application
import android.util.Log
import com.molina.suite.core.common.Activities
import com.molina.suite.core.common.ActivitySource
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Server lokal kecil (hanya 127.0.0.1) yang dipakai pembungkus molina-ytdlp untuk menyelesaikan
 * URL YouTube lewat NewPipeExtractor di dalam aplikasi, tanpa menjalankan yt-dlp di proot.
 *
 * Protokol (teks, satu permintaan per koneksi): klien mengirim tiga baris "token", "url",
 * "selector ytdl-format"; server menjawab "OK\n<json satu baris>\n" atau "ERR <pesan>\n".
 * Port dan token acak ditulis ke berkas bridge di filesDir (privat aplikasi) setiap proses mulai.
 * Bila server tidak ada atau menjawab ERR, pembungkus jatuh ke yt-dlp seperti biasa.
 */
internal object NewPipeYtdlServer {

    private const val TAG = "NewPipeYtdlServer"
    private const val BRIDGE_FILE = "mpv-newpipe.bridge"
    private const val BACKLOG = 8
    private const val READ_TIMEOUT_MS = 5_000
    private const val MAX_LINE_BYTES = 4_096
    private const val TOKEN_BYTES = 16

    private val started = AtomicBoolean(false)
    private val resolver = NewPipeStreamResolver()
    private val workers = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "mpv-newpipe-ytdl").apply { isDaemon = true }
    }

    @Volatile
    private var token: String = ""

    /** Memulai server sekali per proses; kegagalan hanya dicatat (pembungkus memakai yt-dlp). */
    fun start(application: Application) {
        if (!started.compareAndSet(false, true)) return
        val socket = try {
            ServerSocket(0, BACKLOG, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        } catch (e: IOException) {
            Log.w(TAG, "Server NewPipe tidak bisa dibuka", e)
            started.set(false)
            return
        }
        token = newToken()
        if (!writeBridgeFile(File(application.filesDir, BRIDGE_FILE), socket.localPort)) {
            socket.close()
            started.set(false)
            return
        }
        Thread({ acceptLoop(socket) }, "mpv-newpipe-accept").apply { isDaemon = true }.start()
    }

    private fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { String.format("%02x", it.toInt() and 0xff) }
    }

    private fun writeBridgeFile(target: File, port: Int): Boolean {
        val tmp = File(target.parentFile, BRIDGE_FILE + ".tmp")
        return try {
            tmp.writeText("$port $token\n")
            tmp.setReadable(false, false)
            tmp.setReadable(true, true)
            tmp.setWritable(false, false)
            tmp.setWritable(true, true)
            if (!tmp.renameTo(target)) throw IOException("rename gagal")
            true
        } catch (e: IOException) {
            Log.w(TAG, "Berkas bridge tidak bisa ditulis", e)
            tmp.delete()
            false
        }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (!server.isClosed) {
            val client = try {
                server.accept()
            } catch (e: IOException) {
                if (!server.isClosed) Log.w(TAG, "accept gagal", e)
                return
            }
            workers.execute { handle(client) }
        }
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            try {
                socket.soTimeout = READ_TIMEOUT_MS
                val reply = respond(socket.getInputStream())
                socket.getOutputStream().apply {
                    write(reply.toByteArray(Charsets.UTF_8))
                    flush()
                }
            } catch (e: IOException) {
                // Klien (pembungkus) sudah pergi atau terlambat mengirim; tidak ada yang perlu dijawab.
            }
        }
    }

    private fun respond(input: InputStream): String {
        val givenToken = readLine(input)
        val url = readLine(input)
        val selector = readLine(input)
        val expected = token
        if (expected.isEmpty() ||
            !MessageDigest.isEqual(givenToken.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
        ) {
            return "ERR token\n"
        }
        val id = YoutubeWatchUrl.videoId(url) ?: return "ERR url\n"
        val maxHeight = StreamPicker.maxHeight(selector)
        return Activities.log.track(ActivitySource.MPV, "Putar YouTube (NewPipe)") { entry ->
            try {
                val json = resolver.resolve(id, maxHeight)
                entry.end(true, "${maxHeight}p")
                "OK\n$json\n"
            } catch (e: NewPipeStreamResolver.ResolveException) {
                val message = MpvLogText.redact(e.message ?: "gagal")
                entry.end(false, message)
                "ERR " + message.replace('\n', ' ') + "\n"
            }
        }
    }

    /** Membaca satu baris UTF-8 (tanpa \r\n) dengan batas panjang; melempar IOException bila terlalu panjang atau terputus. */
    private fun readLine(input: InputStream): String {
        val buffer = java.io.ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next < 0) throw IOException("koneksi terputus")
            if (next == '\n'.code) break
            if (next != '\r'.code) buffer.write(next)
            if (buffer.size() > MAX_LINE_BYTES) throw IOException("baris terlalu panjang")
        }
        return buffer.toString(Charsets.UTF_8.name())
    }
}
