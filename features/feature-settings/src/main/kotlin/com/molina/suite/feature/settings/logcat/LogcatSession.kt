package com.molina.suite.feature.settings.logcat

import java.io.Closeable

/**
 * Satu sesi pembacaan logcat: memegang [buffer] dan pembaca, tanpa mengenal tampilan. Layar cukup
 * memanggil [start] dan [stop] sesuai siklus hidupnya lalu membaca [buffer], [running], dan
 * [endMessage]. Callback pembaca dari sesi yang sudah dihentikan diabaikan, jadi memulai ulang
 * tidak tercampur dengan sisa thread lama.
 */
internal class LogcatSession(
    capacity: Int = LogcatBuffer.DEFAULT_CAPACITY,
    private val reader: LogcatReader = LogcatReader()
) {

    val buffer = LogcatBuffer(capacity)

    /** True selama pembaca berjalan. */
    @Volatile var running: Boolean = false
        private set

    /** Alasan pembaca berhenti sendiri (bukan lewat [stop]); null bila masih berjalan atau dihentikan sengaja. */
    @Volatile var endMessage: String? = null
        private set

    private val lock = Any()
    private var handle: Closeable? = null
    private var generation = 0

    /** Mulai membaca; tidak berbuat apa-apa bila sudah berjalan. */
    fun start() {
        synchronized(lock) {
            if (handle != null) return
            generation++
            val mine = generation
            endMessage = null
            running = true
            handle = reader.start(object : LogcatReader.Listener {
                override fun onEntry(entry: LogcatEntry) {
                    if (isCurrent(mine)) buffer.add(entry)
                }

                override fun onEnd(error: String?) {
                    synchronized(lock) {
                        if (generation != mine || handle == null) return
                        running = false
                        endMessage = error ?: UNEXPECTED_END
                        handle = null
                    }
                }
            })
        }
    }

    /** Berhenti membaca; isi [buffer] dipertahankan. */
    fun stop() {
        val toClose: Closeable?
        synchronized(lock) {
            toClose = handle
            handle = null
            generation++
            running = false
        }
        toClose?.close()
    }

    private fun isCurrent(mine: Int): Boolean = synchronized(lock) { generation == mine && handle != null }

    private companion object {
        const val UNEXPECTED_END = "logcat berhenti tanpa pesan."
    }
}
