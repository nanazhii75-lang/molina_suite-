package com.molina.suite.feature.settings.logcat

import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader

/**
 * Membaca logcat milik aplikasi ini sendiri. Aplikasi biasa hanya boleh melihat log dengan UID
 * miliknya (tanpa izin READ_LOGS), jadi log mpv, Terminal, dan Code tetap terbaca sedangkan log
 * aplikasi lain tidak. Proses logcat dijalankan langsung dari aplikasi, bukan lewat terminal.
 *
 * [command] bisa diganti agar pembaca diuji tanpa perangkat Android.
 */
internal class LogcatReader(
    private val command: List<String> = DEFAULT_COMMAND
) {

    /** Penerima hasil baca; semua callback dipanggil dari thread pembaca, bukan thread utama. */
    interface Listener {
        fun onEntry(entry: LogcatEntry)

        /** Pembacaan berakhir; [error] null bila logcat selesai normal atau dihentikan lewat [Closeable.close]. */
        fun onEnd(error: String?)
    }

    /** Mulai membaca di thread tersendiri; panggil [Closeable.close] pada hasilnya untuk berhenti. */
    fun start(listener: Listener): Closeable {
        val session = Session(command, listener)
        session.begin()
        return session
    }

    private class Session(
        private val command: List<String>,
        private val listener: Listener
    ) : Closeable {

        @Volatile private var process: Process? = null
        @Volatile private var stopped = false

        fun begin() {
            Thread({ read() }, "LogcatReader").apply { isDaemon = true }.start()
        }

        private fun read() {
            var error: String? = null
            try {
                val started = ProcessBuilder(command).redirectErrorStream(true).start()
                process = started
                if (stopped) started.destroy()
                BufferedReader(InputStreamReader(started.inputStream, Charsets.UTF_8)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        LogcatParser.parse(line)?.let { listener.onEntry(it) }
                    }
                }
                if (!stopped) {
                    val code = started.waitFor()
                    if (code != 0) error = "logcat berhenti dengan kode keluar $code"
                }
            } catch (e: IOException) {
                if (!stopped) error = "Tidak bisa menjalankan logcat: ${e.message ?: e.javaClass.simpleName}"
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                process?.destroy()
                listener.onEnd(error)
            }
        }

        override fun close() {
            stopped = true
            process?.destroy()
        }
    }

    companion object {
        /** Format threadtime memuat waktu, PID, TID, prioritas, tag, dan pesan di satu baris. */
        val DEFAULT_COMMAND: List<String> = listOf("logcat", "-v", "threadtime")

        /**
         * Mengambil [count] baris logcat terakhir milik aplikasi ini lalu berhenti (logcat -d),
         * dengan batas waktu [timeoutMillis]. Dipakai pencatat crash, jadi tidak melempar; hasilnya
         * kosong bila logcat tidak bisa dijalankan atau belum menghasilkan keluaran saat waktunya habis.
         */
        fun dumpTail(
            count: Int,
            timeoutMillis: Long,
            command: List<String> = listOf("logcat", "-d", "-v", "threadtime", "-t", count.toString())
        ): List<String> {
            val lines = ArrayList<String>()
            val worker = Thread({
                try {
                    val process = ProcessBuilder(command).redirectErrorStream(true).start()
                    try {
                        process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                            synchronized(lines) { lines.add(line) }
                        }
                    } finally {
                        process.destroy()
                    }
                } catch (e: IOException) {
                    // Tanpa logcat laporan crash tetap ditulis, hanya tanpa ekor log.
                }
            }, "LogcatDump")
            worker.isDaemon = true
            worker.start()
            try {
                worker.join(timeoutMillis)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            return synchronized(lines) { ArrayList(lines) }
        }

        /**
         * Mengosongkan buffer logcat milik aplikasi ini. Mengembalikan null bila berhasil, atau
         * alasan gagal; tidak melempar.
         */
        fun clearSystemBuffer(): String? = try {
            val process = ProcessBuilder("logcat", "-c").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val code = process.waitFor()
            if (code == 0) null else output.ifEmpty { "logcat -c berhenti dengan kode keluar $code" }
        } catch (e: IOException) {
            "Tidak bisa menjalankan logcat: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
