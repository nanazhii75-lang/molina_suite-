package com.molina.suite.feature.settings.logcat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Menyusun teks laporan crash: keterangan perangkat, stack trace, lalu ekor logcat aplikasi. */
internal object CrashReport {

    private const val FILE_PREFIX = "crash-"

    /** Nama berkas yang terurut menurut waktu, mis. "crash-20261014-063000.txt". */
    fun fileName(time: Date, suffix: Int = 1): String =
        FILE_PREFIX + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(time) +
            (if (suffix > 1) "-$suffix" else "") + ".txt"

    fun isCrashFile(name: String): Boolean = name.startsWith(FILE_PREFIX) && name.endsWith(".txt")

    fun build(
        time: Date,
        threadName: String,
        appVersion: String,
        device: String,
        stackTrace: String,
        logLines: List<String>
    ): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(time)
        val builder = StringBuilder()
        builder.append("Molina crash report\n")
        builder.append("Waktu     : ").append(stamp).append('\n')
        builder.append("Thread    : ").append(threadName).append('\n')
        builder.append("Versi app : ").append(appVersion).append('\n')
        builder.append("Perangkat : ").append(device).append("\n\n")
        builder.append("== Stack trace ==\n")
        builder.append(stackTrace.trimEnd().ifEmpty { "(tidak tersedia)" }).append("\n\n")
        builder.append("== Logcat aplikasi (").append(logLines.size).append(" baris terakhir) ==\n")
        if (logLines.isEmpty()) builder.append("(tidak tersedia)\n") else logLines.forEach { builder.append(it).append('\n') }
        return builder.toString()
    }
}
