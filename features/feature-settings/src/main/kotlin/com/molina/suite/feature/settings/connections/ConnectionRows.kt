package com.molina.suite.feature.settings.connections

import com.molina.suite.core.common.ActivitySource
import com.molina.suite.core.common.ConnectionLedger

/** Satu baris tampilan: host (plus port bila bukan bawaan), enkripsi, asal, jumlah, dan waktu terakhir. */
internal data class ConnectionRow(
    val hostLabel: String,
    val secure: Boolean,
    val source: ActivitySource,
    val count: Long,
    val lastAtMillis: Long
)

internal data class ConnectionSummary(val hosts: Int, val insecure: Int)

internal object ConnectionRows {

    /** Yang tanpa enkripsi di depan, karena paling perlu dilihat; selebihnya yang terbaru dulu. */
    fun of(entries: List<ConnectionLedger.Entry>): List<ConnectionRow> =
        entries
            .map { ConnectionRow(label(it), it.secure, it.source, it.count, it.lastAtMillis) }
            .sortedWith(compareBy<ConnectionRow> { it.secure }.thenByDescending { it.lastAtMillis })

    /** Jumlah host berbeda, dan berapa di antaranya pernah dihubungi tanpa enkripsi. */
    fun summarize(rows: List<ConnectionRow>): ConnectionSummary = ConnectionSummary(
        hosts = rows.map { it.hostLabel }.toSet().size,
        insecure = rows.filter { !it.secure }.map { it.hostLabel }.toSet().size
    )

    private fun label(entry: ConnectionLedger.Entry): String =
        if (entry.port == null) entry.host else entry.host + ":" + entry.port
}
