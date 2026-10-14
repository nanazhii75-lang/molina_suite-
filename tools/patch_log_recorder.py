#!/usr/bin/env python3
"""Perekam log latar molina-suite (poin 3): riwayat log berputar yang berjalan sejak aplikasi dibuka.

Menambah LogFileStore, LogResumePoint, LogRecorder di feature-settings; layar Logcat membaca dari
LogRecorder (bukan lagi sesi sendiri), LogcatSession dihapus, dan MolinaApplication memulai perekam.
Idempotent. Dry-run secara default; gunakan --apply untuk menulis.
Dijalankan dari root repo: python3 tools/patch_log_recorder.py [--apply]
"""
import argparse
import difflib
import os
import sys


def die(msg):
    print("GAGAL: " + msg)
    sys.exit(1)


def load(path):
    with open(path, "rb") as f:
        raw = f.read().decode("utf-8")
    eol = "\r\n" if "\r\n" in raw else "\n"
    return raw.replace("\r\n", "\n"), eol


def save(path, text, eol):
    parent = os.path.dirname(path)
    if parent:
        os.makedirs(parent, exist_ok=True)
    with open(path, "wb") as f:
        f.write(text.replace("\n", eol).encode("utf-8"))


LOGCAT = "features/feature-settings/src/main/kotlin/com/molina/suite/feature/settings/logcat/"
FRAGMENT = LOGCAT + "LogcatFragment.kt"
SESSION = LOGCAT + "LogcatSession.kt"
MODULE = "features/feature-settings/src/main/kotlin/com/molina/suite/feature/settings/SettingsEngineModule.kt"
APPLICATION = "app/src/main/kotlin/com/molina/suite/MolinaApplication.kt"

NEW_FILES = {
    "features/feature-settings/src/main/kotlin/com/molina/suite/feature/settings/logcat/LogFileStore.kt": r'''package com.molina.suite.feature.settings.logcat

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
''',
    "features/feature-settings/src/main/kotlin/com/molina/suite/feature/settings/logcat/LogResumePoint.kt": r'''package com.molina.suite.feature.settings.logcat

/**
 * Titik lanjut pembacaan logcat setelah pembaca dimulai ulang atau aplikasi dijalankan kembali:
 * mengingat cap waktu entri terakhir yang sudah tercatat beserta baris-baris yang punya cap waktu
 * itu. Logcat dimulai ulang dari cap waktu tersebut ([since]); baris yang sudah tercatat pada cap
 * waktu yang sama dibuang lewat [accept], sehingga tidak ada baris ganda dan tidak ada yang hilang.
 * Aman dipakai dari beberapa thread.
 */
internal class LogResumePoint {

    private val lock = Any()
    private var stamp: String? = null
    private val atStamp = HashMap<String, Int>()
    private val toSkip = HashMap<String, Int>()

    /** Cap waktu "MM-DD HH:MM:SS.mmm" entri terakhir; null bila belum ada yang tercatat. */
    val since: String? get() = synchronized(lock) { stamp }

    /** Mencatat [entry] yang sudah tersimpan (mis. dibaca dari riwayat di disk). */
    fun record(entry: LogcatEntry) {
        synchronized(lock) { note(entry) }
    }

    /** Bersiap menerima pembacaan baru yang dimulai dari [since]: baris tercatat akan dibuang. */
    fun beginResume() {
        synchronized(lock) {
            toSkip.clear()
            toSkip.putAll(atStamp)
        }
    }

    /** True bila [entry] baru dan perlu dicatat; false bila sudah tercatat sebelum dilanjutkan. */
    fun accept(entry: LogcatEntry): Boolean = synchronized(lock) {
        if (toSkip.isNotEmpty()) {
            if (stampOf(entry) == stamp) {
                val line = entry.toLine()
                val remaining = toSkip[line]
                if (remaining != null) {
                    if (remaining <= 1) toSkip.remove(line) else toSkip[line] = remaining - 1
                    return@synchronized false
                }
            } else {
                toSkip.clear()
            }
        }
        note(entry)
        true
    }

    private fun note(entry: LogcatEntry) {
        val current = stampOf(entry)
        if (current != stamp) {
            stamp = current
            atStamp.clear()
        }
        val line = entry.toLine()
        atStamp[line] = (atStamp[line] ?: 0) + 1
    }

    private fun stampOf(entry: LogcatEntry): String = entry.date + " " + entry.time
}
''',
    "features/feature-settings/src/main/kotlin/com/molina/suite/feature/settings/logcat/LogRecorder.kt": r'''package com.molina.suite.feature.settings.logcat

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
    private val worker: ScheduledExecutorService = newWorker()
) {

    /** Alasan perekam berhenti total; null selama masih berjalan atau akan mencoba lagi. */
    @Volatile var endMessage: String? = null
        private set

    private val resume = LogResumePoint()
    private val pendingLock = Any()
    private val pending = ArrayList<String>()
    private var flushPending = false

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
        worker.execute(launching { loadHistory(); launchReader() })
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
            resume.record(entry)
        }
    }

    private fun launchReader() {
        val command = nextCommand()
        produced = false
        readerFactory(command).start(object : LogcatReader.Listener {
            override fun onEntry(entry: LogcatEntry) {
                produced = true
                if (!resume.accept(entry)) return
                buffer.add(entry)
                enqueue(entry.toLine())
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
        private const val FLUSH_DELAY_MS = 3000L
        private const val FLUSH_LINES = 200
        private const val MAX_FAILURES = 8
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
                val recorder = LogRecorder(LogFileStore(history))
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
''',
}

# (berkas, [(lama, baru, jumlah_harus_ada)], penanda sudah diterapkan)
EDITS = [
    (
        FRAGMENT,
        [
            (
                " * level, filter level, pencarian, jeda, bersihkan, dan gulir otomatis. Pembacaan ada di\n"
                " * [LogcatSession] dan dimulai saat layar dibuka, berhenti saat layar ditutup. Saat dijeda, tampilan\n"
                " * berhenti diperbarui tetapi log tetap terkumpul di buffer.\n",
                " * level, filter level, pencarian, jeda, bersihkan, dan gulir otomatis. Pembacaan dan riwayat ada di\n"
                " * [LogRecorder] yang berjalan sejak aplikasi dibuka, jadi layar ini langsung menampilkan log yang\n"
                " * sudah terkumpul. Saat dijeda, tampilan berhenti diperbarui tetapi log tetap terkumpul di buffer.\n",
                1,
            ),
            (
                "    private val session = LogcatSession()\n",
                "    private val recorder by lazy(LazyThreadSafetyMode.NONE) { LogRecorder.get(requireContext()) }\n",
                1,
            ),
            ("        session.start()\n", "", 1),
            ("        session.stop()\n        adapter = null\n", "        adapter = null\n", 1),
            (
                "    /**\n"
                "     * Mengosongkan tampilan dan buffer, lalu mencoba mengosongkan buffer sistem supaya log lama\n"
                "     * tidak muncul lagi bila pembacaan dimulai ulang. Kegagalan langkah kedua tidak mengganggu\n"
                "     * tampilan, jadi hasilnya sengaja tidak dilaporkan.\n"
                "     */\n"
                "    private fun clear() {\n"
                "        session.buffer.clear()\n"
                "        refresh(true)\n"
                "        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) { LogcatReader.clearSystemBuffer() }\n"
                "    }\n",
                "    /**\n"
                "     * Mengosongkan tampilan, riwayat di disk, dan buffer sistem lewat [LogRecorder]. Kegagalan\n"
                "     * menghapus riwayat tidak mengganggu tampilan, jadi hasilnya sengaja tidak dilaporkan.\n"
                "     */\n"
                "    private fun clear() {\n"
                "        recorder.clear()\n"
                "        refresh(true)\n"
                "    }\n",
                1,
            ),
            ("session.buffer", "recorder.buffer", 5),
            ("session.endMessage", "recorder.endMessage", 1),
        ],
        "LogRecorder.get(requireContext())",
    ),
    (
        MODULE,
        [
            (
                "import com.molina.suite.feature.settings.logcat.CrashRecorder\n",
                "import com.molina.suite.feature.settings.logcat.CrashRecorder\n"
                "import com.molina.suite.feature.settings.logcat.LogRecorder\n",
                1,
            ),
            (
                "    fun installCrashRecorder(application: Application) = CrashRecorder.install(application)\n",
                "    fun installCrashRecorder(application: Application) = CrashRecorder.install(application)\n"
                "\n"
                "    /** Memulai perekam log latar: log mpv, Terminal, dan Code terkumpul sejak aplikasi dibuka. */\n"
                "    fun installLogRecorder(application: Application) = LogRecorder.install(application)\n",
                1,
            ),
        ],
        "installLogRecorder",
    ),
    (
        APPLICATION,
        [
            (
                "        SettingsEngineModule.installCrashRecorder(this)\n",
                "        SettingsEngineModule.installCrashRecorder(this)\n"
                "        SettingsEngineModule.installLogRecorder(this)\n",
                1,
            ),
        ],
        "installLogRecorder",
    ),
]

UNUSED_IMPORT = "import kotlinx.coroutines.Dispatchers\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    pending = []  # (path, teks_baru, eol)
    removals = []

    for path, content in NEW_FILES.items():
        if os.path.exists(path):
            existing, _ = load(path)
            if existing == content:
                print("SUDAH: " + path)
                continue
            die(path + " sudah ada dengan isi berbeda; periksa manual")
        print("BARU: %s (%d baris)" % (path, content.count("\n")))
        pending.append((path, content, "\n"))

    for path, edits, marker in EDITS:
        if not os.path.exists(path):
            die("berkas tidak ditemukan: " + path)
        text, eol = load(path)
        if marker in text:
            print("SUDAH: " + path)
            continue
        new = text
        for old, rep, count in edits:
            found = new.count(old)
            if found != count:
                die("%s: potongan harus %d kali, ditemukan %d:\n%s" % (path, count, found, old))
            new = new.replace(old, rep)
        if path == FRAGMENT:
            if new.count("Dispatchers") == 1 and UNUSED_IMPORT in new:
                new = new.replace(UNUSED_IMPORT, "", 1)
            if "session" in new.lower().replace("savedinstancestate", ""):
                die(path + ": masih ada rujukan 'session' setelah diedit; periksa manual")
        diff = difflib.unified_diff(
            text.splitlines(), new.splitlines(),
            "sebelum/" + path, "sesudah/" + path, lineterm="", n=1,
        )
        print("\n".join(diff))
        pending.append((path, new, eol))

    if os.path.exists(SESSION):
        print("HAPUS: " + SESSION + " (digantikan LogRecorder)")
        removals.append(SESSION)

    if not pending and not removals:
        print("Tidak ada perubahan (sudah diterapkan).")
        return

    if not args.apply:
        print("DRY-RUN: jalankan lagi dengan --apply")
        return

    for path, text, eol in pending:
        save(path, text, eol)
    for path in removals:
        os.remove(path)
    print("DITERAPKAN: %d berkas ditulis, %d dihapus" % (len(pending), len(removals)))


if __name__ == "__main__":
    main()
