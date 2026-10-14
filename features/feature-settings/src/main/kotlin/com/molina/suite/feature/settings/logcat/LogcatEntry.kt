package com.molina.suite.feature.settings.logcat

/** Satu baris logcat format threadtime yang sudah dipecah; [date] "MM-DD", [time] "HH:MM:SS.mmm". */
internal data class LogcatEntry(
    val date: String,
    val time: String,
    val pid: Int,
    val tid: Int,
    val level: LogcatLevel,
    val tag: String,
    val message: String,
    /** Jumlah baris identik berurutan yang digabung; 1 untuk baris biasa. */
    val repeat: Int = 1
) {
    /** Tingkat sebenarnya (lihat [LogSignals.severity]); untuk penyaring, warna, dan hitungan masalah. */
    val severity: LogcatLevel get() = LogSignals.severity(this)

    /** Bentuk teks satu baris untuk ekspor atau salin; bila dibaca ulang parser hasilnya sama. */
    fun toLine(): String = "$date $time ${pid.toString().padStart(5)} ${tid.toString().padStart(5)} " +
        "${level.symbol} $tag: $message"
}
