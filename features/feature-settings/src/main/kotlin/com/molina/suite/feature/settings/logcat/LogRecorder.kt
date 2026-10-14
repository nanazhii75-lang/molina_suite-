package com.molina.suite.feature.settings.logcat

import android.app.Application
import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * Perekam log molina yang berjalan sejak aplikasi dibuka, bukan hanya saat layar Logcat tampil.
 * Log aplikasi sendiri (mpv, Terminal, Code) dibaca dari logcat, disimpan di [buffer] untuk
 * tampilan, dan ditulis berkelompok ke riwayat berputar di [store], jadi log sebelum layar dibuka
 * dan log sebelum aplikasi dimulai ulang tetap ada. Saat dimulai, [buffer] diisi dari riwayat dan
 * logcat dilanjutkan dari entri terakhir yang tercatat ([LogResumePoint]).
 *
 * Bila logcat berhenti, pembaca dimulai ulang dengan jeda yang makin panjang; setelah gagal
 * berturut-turut [MAX_FAILURES] kali tanpa menghasilkan satu baris pun, perekam menyerah dan
 * alasannya tersedia di [endMessage]. Kegagalan menulis ke disk tidak mengganggu: baris tetap ada
 * di [buffer] selama proses hidup. Perekam tidak menulis log sendiri supaya tidak merekam dirinya.
 */
internal class LogRecorder(
    private val store: LogFileStore,
    val buffer: LogcatBuffer = LogcatBuffer(),
    private val readerFactory: (List<String>) -> LogcatReader = { command -> LogcatReader(command) },
    private val now: () -> String = { currentStamp() },
    private val restartNote: (String) -> LogcatEntry? = { null },
    private val worker: ScheduledExecutorService = newWorker()
) {

    /** Alasan perekam berhenti total; null selama masih berjalan atau akan mencoba lagi. */
    @Volatile var endMessage: String? = null
        private set

    private val resume = LogResumePoint()
    private val pendingLock = Any()
    private val pending = ArrayList<String>()
    private var flushPending = false

    private val ownPid = android.os.Process.myPid()
    private val markerLock = Any()
    private var pendingMarker: LogcatEntry? = null

    private var started = false
    @Volatile private var failures = 0
    @Volatile private var timeResumeSupported = true
    @Volatile private var usedTimeResume = false
    @Volatile private var produced = false

    /** Memulai perekaman; tidak berbuat apa-apa bila sudah dimulai. */
    fun start() {
        synchronized(this) {
            if (started) return
            started = true
        }
        worker.execute(launching { loadHistory(); noteRestart(); launchReader() })
    }

    /** Mengosongkan tampilan, riwayat di disk, dan buffer logcat sistem. */
    fun clear() {
        buffer.clear()
        synchronized(pendingLock) { pending.clear() }
        worker.execute(safely {
            store.clear()
            LogcatReader.clearSystemBuffer()
        })
    }

    private fun loadHistory() {
        for (line in store.readTail(buffer.capacity)) {
            val entry = LogcatParser.parse(line) ?: continue
            buffer.add(entry)
            if (entry.tag != ProcessMarker.TAG) resume.record(entry)
        }
    }

    /**
     * Bila proses sebelumnya berhenti, menyiapkan penanda "proses berhenti, dimulai ulang". Penanda
     * baru dipasang tepat sebelum baris pertama proses ini ([releaseMarker]) supaya baris terakhir
     * proses lama, yang dibaca belakangan dari logcat, tetap berada di atasnya. Penanda tidak ikut
     * titik lanjut logcat, jadi tidak ada baris proses lama yang terlewat.
     */
    private fun noteRestart() {
        val entry = restartNote(now()) ?: return
        synchronized(markerLock) { pendingMarker = entry }
        worker.schedule(safely { releaseMarker() }, MARKER_WAIT_MS, TimeUnit.MILLISECONDS)
    }

    private fun releaseMarker() {
        val entry = synchronized(markerLock) {
            val waiting = pendingMarker
            pendingMarker = null
            waiting
        } ?: return
        buffer.add(entry)
        enqueue(entry.toLine())
    }

    private fun launchReader() {
        val command = nextCommand()
        produced = false
        readerFactory(command).start(object : LogcatReader.Listener {
            override fun onEntry(entry: LogcatEntry) {
                produced = true
                if (!resume.accept(entry)) return
                if (entry.pid == ownPid) releaseMarker()
                buffer.add(entry)
                if (entry.level >= PERSIST_MIN_LEVEL) enqueue(entry.toLine())
            }

            override fun onEnd(error: String?) {
                onReaderEnded(error)
            }
        })
    }

    /**
     * Perintah logcat berikutnya: seluruh buffer bila belum ada riwayat, atau lanjut dari cap waktu
     * terakhir. Bila cap waktu tidak bisa dipakai (logcat menolaknya, atau tampak di masa depan
     * karena pergantian tahun), cukup mulai dari baris terbaru.
     */
    private fun nextCommand(): List<String> {
        val since = resume.since
        resume.beginResume()
        usedTimeResume = false
        return when {
            since == null -> LogcatReader.DEFAULT_COMMAND
            timeResumeSupported && since <= now() -> {
                usedTimeResume = true
                listOf("logcat", "-v", "threadtime", "-T", since)
            }
            else -> listOf("logcat", "-v", "threadtime", "-T", "1")
        }
    }

    private fun onReaderEnded(error: String?) {
        if (produced) {
            failures = 0
        } else {
            failures++
            if (usedTimeResume) timeResumeSupported = false
        }
        if (failures >= MAX_FAILURES) {
            endMessage = error ?: UNEXPECTED_END
            return
        }
        val delay = if (produced) {
            RESTART_BASE_MS
        } else {
            minOf(RESTART_MAX_MS, RESTART_BASE_MS shl minOf(failures, BACKOFF_MAX_SHIFT))
        }
        worker.schedule(launching { launchReader() }, delay, TimeUnit.MILLISECONDS)
    }

    private fun enqueue(line: String) {
        var scheduleFlush = false
        var flushEarly = false
        synchronized(pendingLock) {
            pending.add(line)
            if (!flushPending) {
                flushPending = true
                scheduleFlush = true
            }
            if (pending.size == FLUSH_LINES) flushEarly = true
        }
        if (scheduleFlush) worker.schedule(safely { flush() }, FLUSH_DELAY_MS, TimeUnit.MILLISECONDS)
        if (flushEarly) worker.execute(safely { flush() })
    }

    private fun flush() {
        val batch = synchronized(pendingLock) {
            flushPending = false
            if (pending.isEmpty()) return
            ArrayList(pending).also { pending.clear() }
        }
        store.append(batch)
    }

    /** Tugas awal/ulang pembaca: kegagalan tak terduga menghentikan perekam dan dilaporkan. */
    private fun launching(task: () -> Unit) = Runnable {
        try {
            task()
        } catch (e: Exception) {
            endMessage = "Perekam log tidak bisa dimulai: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Tugas pendukung (tulis, bersihkan): kegagalannya hanya berarti log tidak tersimpan. */
    private fun safely(task: () -> Unit) = Runnable {
        try {
            task()
        } catch (e: Exception) {
            // Tidak ada yang bisa dilakukan; log tetap ada di buffer selama proses hidup.
        }
    }

    companion object {
        private const val HISTORY_DIR_NAME = "logcat-history"

        /** Tingkat terendah yang ditulis ke disk; di bawahnya hanya ada di buffer selama proses hidup. */
        private val PERSIST_MIN_LEVEL = LogcatLevel.INFO
        private const val FLUSH_DELAY_MS = 3000L
        private const val FLUSH_LINES = 200
        private const val MAX_FAILURES = 8
        private const val MARKER_WAIT_MS = 4000L
        private const val RESTART_BASE_MS = 1000L
        private const val RESTART_MAX_MS = 30000L
        private const val BACKOFF_MAX_SHIFT = 5
        private const val UNEXPECTED_END = "logcat berhenti tanpa pesan."

        @Volatile private var instance: LogRecorder? = null

        /** Perekam milik aplikasi; dibuat dan dimulai pada panggilan pertama. */
        fun get(context: Context): LogRecorder {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val appContext = context.applicationContext
                val history = File(appContext.noBackupFilesDir, HISTORY_DIR_NAME)
                val recorder = LogRecorder(
                    LogFileStore(history),
                    restartNote = { stamp -> RestartNotice.detect(appContext, stamp) }
                )
                instance = recorder
                recorder.start()
                return recorder
            }
        }

        /** Memulai perekaman sejak aplikasi dibuka; aman dipanggil berulang. */
        fun install(application: Application) {
            get(application)
        }
    }
}

private fun currentStamp(): String =
    SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date())

private fun newWorker(): ScheduledExecutorService =
    ScheduledThreadPoolExecutor(1, ThreadFactory { runnable ->
        Thread(runnable, "LogRecorder").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    })
