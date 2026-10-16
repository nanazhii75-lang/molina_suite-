package com.molina.suite.feature.mpv

/** Metadata video yang ikut dalam JSON agar mpv menampilkan judul dan durasi yang benar. */
internal data class VideoMeta(
    val id: String,
    val title: String,
    val webpageUrl: String,
    val durationSec: Long,
    val uploader: String?,
    val thumbnail: String?
)

/**
 * Membuat satu baris JSON berbentuk keluaran `yt-dlp -J` untuk satu video, hanya berisi bidang
 * yang dibaca ytdl_hook mpv. Ditulis tangan (tanpa org.json) agar murni dan bisa diuji di JVM.
 */
internal object YtdlJson {

    /** Video+audio terpisah (requested_formats) atau satu stream gabungan sesuai [choice]. */
    fun forChoice(meta: VideoMeta, choice: StreamChoice, userAgent: (StreamClient) -> String): String {
        val combined = choice.combined
        if (combined != null) {
            // Satu stream: bidang format (format_id, ext, url, header) langsung di tingkat atas.
            return obj(
                baseFields(meta) +
                    trackFields(combined, video = true, audio = true, userAgent = userAgent) +
                    listOf("is_live" to "false")
            )
        }
        val tracks = listOfNotNull(
            choice.video?.let { obj(trackFields(it, video = true, audio = false, userAgent = userAgent)) },
            choice.audio?.let { obj(trackFields(it, video = false, audio = true, userAgent = userAgent)) }
        )
        val ids = listOfNotNull(choice.video?.formatId, choice.audio?.formatId).joinToString("+")
        return obj(
            baseFields(meta) + listOf(
                "format_id" to str(ids),
                "ext" to str(choice.video?.ext ?: choice.audio?.ext ?: "mp4"),
                "requested_formats" to "[" + tracks.joinToString(",") + "]",
                "is_live" to "false"
            )
        )
    }

    /** Siaran langsung: satu URL HLS. */
    fun forLive(meta: VideoMeta, hlsUrl: String, userAgent: String): String =
        obj(
            baseFields(meta) + listOf(
                "url" to str(hlsUrl),
                "protocol" to str("m3u8_native"),
                "ext" to str("mp4"),
                "is_live" to "true",
                "http_headers" to obj(listOf("User-Agent" to str(userAgent)))
            )
        )

    private fun baseFields(meta: VideoMeta): List<Pair<String, String>> {
        val fields = mutableListOf(
            "_type" to str("video"),
            "id" to str(meta.id),
            "title" to str(meta.title),
            "webpage_url" to str(meta.webpageUrl),
            "original_url" to str(meta.webpageUrl),
            "extractor" to str("youtube"),
            "extractor_key" to str("Youtube")
        )
        if (meta.durationSec > 0) fields += "duration" to meta.durationSec.toString()
        meta.uploader?.takeIf { it.isNotBlank() }?.let { fields += "uploader" to str(it) }
        meta.thumbnail?.takeIf { it.isNotBlank() }?.let { fields += "thumbnail" to str(it) }
        return fields
    }

    private fun trackFields(
        track: MediaCandidate,
        video: Boolean,
        audio: Boolean,
        userAgent: (StreamClient) -> String
    ): List<Pair<String, String>> {
        val codec = track.codec?.takeIf { it.isNotBlank() } ?: "unknown"
        val fields = mutableListOf(
            "format_id" to str(track.formatId),
            "url" to str(track.url),
            "ext" to str(track.ext),
            "protocol" to str("https"),
            "vcodec" to str(if (video) codec else "none"),
            "acodec" to str(if (audio) (if (video) "unknown" else codec) else "none")
        )
        if (video) {
            if (track.width > 0) fields += "width" to track.width.toString()
            if (track.height > 0) fields += "height" to track.height.toString()
            if (track.fps > 0) fields += "fps" to track.fps.toString()
        }
        if (track.bitrateKbps > 0) {
            fields += "tbr" to track.bitrateKbps.toString()
            if (video && !audio) fields += "vbr" to track.bitrateKbps.toString()
            if (audio && !video) fields += "abr" to track.bitrateKbps.toString()
        }
        fields += "http_headers" to obj(listOf("User-Agent" to str(userAgent(track.client))))
        return fields
    }

    private fun obj(fields: List<Pair<String, String>>): String =
        fields.joinToString(",", "{", "}") { (key, value) -> str(key) + ":" + value }

    /** Literal string JSON dengan escape penuh (kutip, backslash, karakter kontrol, U+2028/9). */
    internal fun str(value: String): String {
        val out = StringBuilder(value.length + 2).append('"')
        for (ch in value) {
            when {
                ch == '"' -> out.append("\\\"")
                ch == '\\' -> out.append("\\\\")
                ch == '\n' -> out.append("\\n")
                ch == '\r' -> out.append("\\r")
                ch == '\t' -> out.append("\\t")
                ch < ' ' || ch == '\u2028' || ch == '\u2029' ->
                    out.append(String.format("\\u%04x", ch.code))
                else -> out.append(ch)
            }
        }
        return out.append('"').toString()
    }
}
