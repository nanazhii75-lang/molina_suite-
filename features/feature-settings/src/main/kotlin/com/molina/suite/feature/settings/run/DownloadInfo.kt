package com.molina.suite.feature.settings.run

/** Kartu-kartu Quick Downloader yang diturunkan dari status skrip download-media.sh. */
internal data class DownloadInfo(
    val title: String?,
    val titleDetail: String?,
    val output: String?,
    val outputDetail: String?,
    val engine: String?,
    val stream: StreamPart?,
    val speed: InfoTile,
    val size: InfoTile,
    val eta: InfoTile
) {
    /** Aliran ke-[index] dari [count]; hanya ada bila video dan audio diunduh terpisah. */
    data class StreamPart(val index: Int, val count: Int)

    companion object {
        /**
         * [ended] true bila proses sudah selesai atau dianggap macet: kartu yang belum punya
         * nilai dianggap gagal, bukan "sedang berjalan".
         */
        fun from(status: RunStatus?, ended: Boolean): DownloadInfo {
            if (status == null) {
                val tile = if (ended) InfoTile.Failed else InfoTile.Pending
                return DownloadInfo(null, null, null, null, null, null, tile, tile, tile)
            }
            val done = status.state == RunState.DONE
            val running = status.state == RunState.RUNNING && !ended

            // Nilai yang belum ada: sedang mengunduh = berjalan; olah akhir = tidak tersedia;
            // selesai = tidak tersedia; gagal atau macet = gagal; tahap awal = menunggu.
            val missing = when {
                done -> InfoTile.Absent
                !running -> InfoTile.Failed
                status.stage == "download" -> InfoTile.Reading
                status.stage == "process" -> InfoTile.Absent
                else -> InfoTile.Pending
            }

            val speed = ProbeFormat.size(status.value("speed_bps")?.toLongOrNull())
                ?.let { InfoTile.Value("$it/s", null) } ?: missing

            val downloaded = status.value("downloaded_bytes")?.toLongOrNull()
            val total = status.value("total_bytes")?.toLongOrNull()
            val size = ProbeFormat.size(downloaded)?.let { now ->
                val detail = if (done) null else ProbeFormat.size(total)?.let { "/ $it" }
                InfoTile.Value(now, detail)
            } ?: missing

            val eta = if (done) {
                InfoTile.Absent
            } else {
                ProbeFormat.duration(status.value("eta_sec")?.toDoubleOrNull())
                    ?.let { InfoTile.Value(it, null) } ?: missing
            }

            val title = status.value("title")
            val host = status.value("host")
            val index = status.value("stream_idx")?.toIntOrNull()
            val count = status.value("stream_count")?.toIntOrNull()
            val stream = if (index != null && count != null && count > 1) {
                StreamPart(index.coerceIn(1, count), count)
            } else {
                null
            }

            return DownloadInfo(
                title = title ?: host,
                titleDetail = if (title != null) host else null,
                output = status.value("out"),
                outputDetail = RunOutput.detail(status),
                engine = status.value("engine"),
                stream = stream,
                speed = speed,
                size = size,
                eta = eta
            )
        }
    }
}
