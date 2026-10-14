package com.molina.suite.core.common

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

/** Asal sebuah aktivitas; [code] dipakai di baris log, [label] untuk tampilan. */
enum class ActivitySource(val code: String, val label: String) {
    APP("app", "Aplikasi"),
    MPV("mpv", "mpv"),
    TERMINAL("term", "Terminal"),
    CODE("code", "Code"),
    SETTING("set", "Setting");

    companion object {
        fun fromCode(code: String): ActivitySource? = values().firstOrNull { it.code == code }
    }
}

/** Prioritas baris aktivitas; sengaja hanya tiga supaya tidak ada yang tercecer di bawah INFO. */
enum class ActivityPriority { INFO, WARN, ERROR }

/** Tujuan baris aktivitas; di aplikasi berupa logcat, di pengujian bisa berupa daftar biasa. */
fun interface ActivitySink {
    fun write(priority: ActivityPriority, message: String)
}

/** Satu baris aktivitas yang sudah dipecah dari teks log; format teksnya dipegang [ActivityLog]. */
sealed interface ActivityLine {
    val id: String

    data class Begin(override val id: String, val source: ActivitySource, val title: String) : ActivityLine
    data class Note(override val id: String, val text: String) : ActivityLine
    data class End(override val id: String, val ok: Boolean, val millis: Long, val detail: String?) : ActivityLine

    companion object {
        private val HEAD = Regex("""^#(\S+) (.*)$""")
        private val BEGIN = Regex("""^(\S+) > (.*)$""")
        private val END = Regex("""^= (ok|gagal) (\d+)ms(?:: (.*))?$""")
        private const val NOTE_PREFIX = "| "

        /** Baris aktivitas dari pesan logcat, atau null bila pesan itu bukan baris aktivitas. */
        fun parse(message: String): ActivityLine? {
            val head = HEAD.find(message) ?: return null
            val id = head.groupValues[1]
            val rest = head.groupValues[2]
            if (rest.startsWith(NOTE_PREFIX)) return Note(id, rest.substring(NOTE_PREFIX.length))
            END.find(rest)?.let { end ->
                val millis = end.groupValues[2].toLongOrNull() ?: return null
                return End(id, end.groupValues[1] == "ok", millis, end.groups[3]?.value)
            }
            BEGIN.find(rest)?.let { begin ->
                val source = ActivitySource.fromCode(begin.groupValues[1]) ?: return null
                return Begin(id, source, begin.groupValues[2])
            }
            return null
        }
    }
}

/**
 * Pencatat aktivitas: satu aksi pengguna (buka berkas, jalankan perintah, pasang alat) menjadi
 * satu aktivitas dengan id, sumber, judul, catatan, dan hasil akhir. Setiap baris ditulis ke
 * [sink] sebagai teks satu baris yang bisa dibaca kembali lewat [ActivityLine.parse], sehingga
 * layar Logcat bisa mengelompokkan log per aktivitas tanpa mengenal modul penulisnya.
 *
 * Isi berkas, argumen perintah, dan data pribadi tidak boleh dicatat; cukup nama dan hasilnya.
 * Kegagalan menulis ke [sink] tidak pernah mengganggu pemanggil. Aman dipakai dari banyak thread.
 */
class ActivityLog(
    private val sink: ActivitySink,
    private val nanoTime: () -> Long = System::nanoTime,
    private val sessionId: String = newSessionId()
) {

    private val counter = AtomicLong()

    /** Memulai aktivitas baru; wajib diakhiri dengan [Activity.end] (atau pakai [track]). */
    fun begin(source: ActivitySource, title: String): Activity {
        val id = "$sessionId.${counter.incrementAndGet()}"
        val activity = Activity(id, nanoTime())
        emit(ActivityPriority.INFO, "#$id ${source.code} > ${clean(title, UNTITLED)}")
        return activity
    }

    /**
     * Menjalankan [block] sebagai satu aktivitas: berakhir "ok" bila selesai biasa, "gagal" bila
     * melempar pengecualian (dilempar lagi), dan "dibatalkan" bila coroutine dibatalkan. [block]
     * boleh mengakhiri aktivitas sendiri lebih dulu bila hasilnya gagal tanpa pengecualian.
     */
    inline fun <T> track(source: ActivitySource, title: String, block: (Activity) -> T): T {
        val activity = begin(source, title)
        val result = try {
            block(activity)
        } catch (e: CancellationException) {
            activity.end(false, "dibatalkan", ActivityPriority.WARN)
            throw e
        } catch (e: Throwable) {
            activity.end(false, e.message ?: e.javaClass.simpleName)
            throw e
        }
        activity.end(true)
        return result
    }

    private fun emit(priority: ActivityPriority, message: String) {
        try {
            sink.write(priority, message)
        } catch (e: Exception) {
            // Pencatatan yang gagal tidak boleh merusak aksi yang sedang dicatat.
        }
    }

    /** Satu aktivitas yang sedang berjalan. */
    inner class Activity internal constructor(val id: String, private val startedAt: Long) {

        private val ended = AtomicBoolean(false)

        /** Menambah satu catatan ke aktivitas ini. */
        fun note(text: String, priority: ActivityPriority = ActivityPriority.INFO) {
            emit(priority, "#$id | ${clean(text, EMPTY_NOTE)}")
        }

        /** Mengakhiri aktivitas; panggilan kedua dan seterusnya diabaikan. */
        fun end(
            ok: Boolean = true,
            detail: String? = null,
            priority: ActivityPriority = if (ok) ActivityPriority.INFO else ActivityPriority.ERROR
        ) {
            if (!ended.compareAndSet(false, true)) return
            val millis = (nanoTime() - startedAt).coerceAtLeast(0L) / NANOS_PER_MILLI
            val tail = detail?.let { clean(it, "") }?.takeIf { it.isNotEmpty() }?.let { ": $it" }.orEmpty()
            emit(priority, "#$id = ${if (ok) "ok" else "gagal"} ${millis}ms$tail")
        }
    }

    companion object {
        /** Tag logcat semua baris aktivitas. */
        const val LOG_TAG = "MolinaAct"

        private const val MAX_TEXT_CHARS = 320
        private const val UNTITLED = "tanpa judul"
        private const val EMPTY_NOTE = "(kosong)"
        private const val NANOS_PER_MILLI = 1_000_000L

        internal fun newSessionId(): String =
            java.lang.Long.toString(System.currentTimeMillis() / 1000L, 36)

        /** Satu baris bersih: tanpa karakter kontrol, spasi berulang dirapatkan, dipotong bila panjang. */
        private fun clean(text: String, fallback: String): String {
            val flat = buildString(text.length) {
                for (ch in text) append(if (ch < ' ') ' ' else ch)
            }.replace(Regex(" {2,}"), " ").trim()
            if (flat.isEmpty()) return fallback
            return if (flat.length > MAX_TEXT_CHARS) flat.take(MAX_TEXT_CHARS - 1) + "\u2026" else flat
        }
    }
}
