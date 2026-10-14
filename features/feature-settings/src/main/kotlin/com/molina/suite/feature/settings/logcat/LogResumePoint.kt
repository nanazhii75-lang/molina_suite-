package com.molina.suite.feature.settings.logcat

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
