package com.molina.suite.feature.settings.logcat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Laporan untuk proses yang berhenti tanpa sempat mencatat crash Java (dimatikan sistem,
 * crash native, ANR): keterangan perangkat, peristiwanya, lalu ekor logcat aplikasi yang masih
 * ada di buffer sistem. Disimpan di folder yang sama dengan laporan crash.
 */
internal object ProcessDeathReport {

    fun build(
        time: Date,
        appVersion: String,
        device: String,
        event: ProcessEvent,
        logLines: List<String>
    ): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(time)
        val builder = StringBuilder()
        builder.append("Molina process-death report\n")
        builder.append("Waktu     : ").append(stamp).append('\n')
        builder.append("Versi app : ").append(appVersion).append('\n')
        builder.append("Perangkat : ").append(device).append("\n\n")
        builder.append("== Peristiwa ==\n")
        builder.append(event.message).append("\n\n")
        builder.append("== Logcat aplikasi (").append(logLines.size).append(" baris terakhir) ==\n")
        if (logLines.isEmpty()) builder.append("(tidak tersedia)\n") else logLines.forEach { builder.append(it).append('\n') }
        return builder.toString()
    }
}
