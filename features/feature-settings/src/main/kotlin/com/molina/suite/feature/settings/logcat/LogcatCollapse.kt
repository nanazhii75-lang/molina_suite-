package com.molina.suite.feature.settings.logcat

/**
 * Menggabungkan baris berurutan yang identik (pid, tingkat, tag, pesan) menjadi satu entri dengan
 * [LogcatEntry.repeat] sebesar jumlahnya; yang dipertahankan adalah kemunculan pertama. Urutan
 * tidak berubah dan baris yang sama tetapi terpisah baris lain tidak digabung.
 */
internal fun List<LogcatEntry>.collapseRepeats(): List<LogcatEntry> {
    if (size < 2) return this
    val out = ArrayList<LogcatEntry>(size)
    var current: LogcatEntry? = null
    for (entry in this) {
        val head = current
        if (head != null && head.pid == entry.pid && head.level == entry.level &&
            head.tag == entry.tag && head.message == entry.message
        ) {
            current = head.copy(repeat = head.repeat + entry.repeat)
        } else {
            if (head != null) out.add(head)
            current = entry
        }
    }
    current?.let { out.add(it) }
    return out
}
