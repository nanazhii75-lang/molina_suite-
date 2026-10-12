package com.molina.suite.feature.settings.run

import java.util.Locale

/** Kartu-kartu Konverter Media yang diturunkan dari status skrip convert-media.sh. */
internal data class ConvertInfo(
    val source: String?,
    val output: String?,
    val outputDetail: String?,
    val speed: InfoTile,
    val size: InfoTile,
    val eta: InfoTile,
    val processed: InfoTile
) {
    companion object {
        /**
         * [ended] true bila proses sudah selesai atau dianggap macet: kartu yang belum punya
         * nilai dianggap gagal, bukan "sedang berjalan".
         */
        fun from(status: RunStatus?, ended: Boolean): ConvertInfo {
            if (status == null) {
                val tile = if (ended) InfoTile.Failed else InfoTile.Pending
                return ConvertInfo(null, null, null, tile, tile, tile, tile)
            }
            val done = status.state == RunState.DONE
            val running = status.state == RunState.RUNNING && !ended
            val converting = status.stage == "convert"

            // Nilai yang belum ada: sedang berjalan = menunggu atau dihitung; selesai = tidak tersedia;
            // gagal atau macet = gagal.
            val missing = when {
                done -> InfoTile.Absent
                !running -> InfoTile.Failed
                converting -> InfoTile.Reading
                else -> InfoTile.Pending
            }

            val speed = status.value("speed")?.toDoubleOrNull()
                ?.let { InfoTile.Value(String.format(Locale.US, "%.1f×", it), null) } ?: missing
            val size = ProbeFormat.size(status.value("size_bytes")?.toLongOrNull())
                ?.let { InfoTile.Value(it, null) } ?: missing

            val total = status.value("duration")?.toDoubleOrNull()
            val current = status.value("time_sec")?.toDoubleOrNull()
            val processed = ProbeFormat.duration(current)?.let { now ->
                InfoTile.Value(now, ProbeFormat.duration(total)?.let { "/ $it" })
            } ?: missing

            val eta = when {
                done -> InfoTile.Absent
                else -> ProbeFormat.duration(status.value("eta_sec")?.toDoubleOrNull())
                    ?.let { InfoTile.Value(it, null) } ?: missing
            }

            val outputDetail = RunOutput.detail(status)

            return ConvertInfo(
                status.value("src"),
                status.value("out"),
                outputDetail,
                speed,
                size,
                eta,
                processed
            )
        }
    }
}
