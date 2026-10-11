package com.molina.suite.feature.settings.run

import java.util.Locale
import kotlin.math.roundToLong

/** Isi satu kartu Info Video. */
internal sealed interface InfoTile {
    /** Proses belum sampai ke tahap pembacaan. */
    object Pending : InfoTile

    /** Sedang dibaca. */
    object Reading : InfoTile

    /** Berkas dibaca, tetapi bagian ini tidak ada (mis. berkas audio tanpa video). */
    object Absent : InfoTile

    /** Proses gagal atau macet sebelum bagian ini terbaca. */
    object Failed : InfoTile

    data class Value(val main: String, val detail: String?) : InfoTile
}

/** Kartu-kartu Info Video yang diturunkan dari status skrip probe-info.sh. */
internal data class ProbeInfo(
    val fileName: String?,
    val fileDetail: String?,
    val duration: InfoTile,
    val resolution: InfoTile,
    val video: InfoTile,
    val audio: InfoTile
) {
    companion object {
        /**
         * [ended] true bila proses sudah selesai atau dianggap macet: kartu yang belum punya
         * nilai dianggap gagal, bukan "sedang dibaca".
         */
        fun from(status: RunStatus?, ended: Boolean): ProbeInfo {
            if (status == null) {
                return placeholder(if (ended) InfoTile.Failed else InfoTile.Pending, null)
            }
            val name = status.value("file")
            val probed = status.value("has_video") != null && status.value("has_audio") != null
            if (!probed) {
                val tile = when {
                    ended || status.state != RunState.RUNNING -> InfoTile.Failed
                    status.stage == "probe" -> InfoTile.Reading
                    else -> InfoTile.Pending
                }
                return placeholder(tile, name)
            }

            val hasVideo = status.value("has_video") == "1"
            val hasAudio = status.value("has_audio") == "1"
            val width = status.value("v_width")?.toIntOrNull()
            val height = status.value("v_height")?.toIntOrNull()

            val duration = ProbeFormat.duration(status.value("duration")?.toDoubleOrNull())
                ?.let { InfoTile.Value(it, null) } ?: InfoTile.Absent

            val resolution = when {
                !hasVideo -> InfoTile.Absent
                width != null && height != null ->
                    InfoTile.Value("$width×$height", ProbeFormat.resolutionClass(height))
                else -> InfoTile.Absent
            }

            val video = if (hasVideo) {
                val fps = status.value("v_fps")?.toDoubleOrNull()?.let { ProbeFormat.fps(it) }
                InfoTile.Value(
                    ProbeFormat.videoCodec(status.value("v_codec")) ?: "—",
                    fps?.let { "$it fps" }
                )
            } else {
                InfoTile.Absent
            }

            val audio = if (hasAudio) {
                val detail = listOfNotNull(
                    ProbeFormat.channels(status.value("a_channels")?.toIntOrNull()),
                    ProbeFormat.sampleRate(status.value("a_rate")?.toIntOrNull()),
                    ProbeFormat.bitrate(status.value("a_bitrate_kbps")?.toIntOrNull())
                )
                InfoTile.Value(
                    ProbeFormat.audioCodec(status.value("a_codec")) ?: "—",
                    detail.takeIf { it.isNotEmpty() }?.joinToString("  •  ")
                )
            } else {
                InfoTile.Absent
            }

            val fileDetail = listOfNotNull(
                status.value("ext"),
                ProbeFormat.size(status.value("size_bytes")?.toLongOrNull()),
                ProbeFormat.bitrate(status.value("bitrate_kbps")?.toIntOrNull())
            ).takeIf { it.isNotEmpty() }?.joinToString("  •  ")

            return ProbeInfo(name, fileDetail, duration, resolution, video, audio)
        }

        private fun placeholder(tile: InfoTile, name: String?): ProbeInfo =
            ProbeInfo(name, null, tile, tile, tile, tile)
    }
}

/** Pemformat tampilan untuk nilai mentah ffprobe. Murni, tanpa Context. */
internal object ProbeFormat {

    private const val KB = 1024L
    private const val MB = KB * 1024
    private const val GB = MB * 1024

    /** Detik menjadi HH:MM:SS; null bila tidak valid. */
    fun duration(seconds: Double?): String? {
        if (seconds == null || seconds.isNaN() || seconds.isInfinite() || seconds < 0) return null
        val total = seconds.roundToLong()
        return String.format(Locale.US, "%02d:%02d:%02d", total / 3600, total % 3600 / 60, total % 60)
    }

    /** Julukan umum berdasarkan tinggi gambar; null bila di bawah SD. */
    fun resolutionClass(height: Int): String? = when {
        height >= 2160 -> "4K"
        height >= 1440 -> "2K"
        height >= 1080 -> "Full HD"
        height >= 720 -> "HD"
        height >= 480 -> "SD"
        else -> null
    }

    fun fps(value: Double): String? {
        if (value.isNaN() || value.isInfinite() || value <= 0) return null
        return if (value == Math.floor(value)) {
            String.format(Locale.US, "%d", value.toLong())
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }

    fun videoCodec(name: String?): String? = when (name) {
        null -> null
        "h264" -> "H.264"
        "hevc" -> "H.265 (HEVC)"
        "vp8" -> "VP8"
        "vp9" -> "VP9"
        "av1" -> "AV1"
        "mpeg4" -> "MPEG-4"
        "mpeg2video" -> "MPEG-2"
        else -> name.uppercase(Locale.ROOT)
    }

    fun audioCodec(name: String?): String? = when (name) {
        null -> null
        "aac" -> "AAC"
        "mp3" -> "MP3"
        "opus" -> "Opus"
        "vorbis" -> "Vorbis"
        "flac" -> "FLAC"
        "ac3" -> "AC3"
        "eac3" -> "E-AC3"
        else -> if (name.startsWith("pcm_")) "PCM" else name.uppercase(Locale.ROOT)
    }

    fun channels(count: Int?): String? = when {
        count == null || count <= 0 -> null
        count == 1 -> "Mono"
        count == 2 -> "Stereo"
        count == 6 -> "5.1"
        count == 8 -> "7.1"
        else -> "$count ch"
    }

    fun sampleRate(hz: Int?): String? {
        if (hz == null || hz <= 0) return null
        val khz = hz / 1000.0
        val text = if (khz == Math.floor(khz)) {
            String.format(Locale.US, "%d", khz.toLong())
        } else {
            String.format(Locale.US, "%.1f", khz)
        }
        return "$text kHz"
    }

    fun bitrate(kbps: Int?): String? = when {
        kbps == null || kbps <= 0 -> null
        kbps >= 1000 -> String.format(Locale.US, "%.1f Mbps", kbps / 1000.0)
        else -> "$kbps kbps"
    }

    fun size(bytes: Long?): String? = when {
        bytes == null || bytes < 0 -> null
        bytes >= GB -> String.format(Locale.US, "%.2f GB", bytes.toDouble() / GB)
        bytes >= MB -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / MB)
        bytes >= KB -> String.format(Locale.US, "%d KB", bytes / KB)
        else -> "$bytes B"
    }
}
