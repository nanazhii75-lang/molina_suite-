package com.molina.suite.feature.mpv

/** Klien YouTube asal sebuah URL stream; menentukan User-Agent yang harus dipakai pemutar. */
internal enum class StreamClient { IOS, ANDROID, VISIONOS, WEB }

/** Satu stream yang bisa diputar langsung lewat HTTP(S), tanpa ketergantungan pada kelas NewPipe. */
internal data class MediaCandidate(
    val formatId: String,
    val url: String,
    /** Ekstensi wadah: mp4, m4a, webm, ... */
    val ext: String,
    /** Kode codec (avc1.640028, vp9, mp4a.40.2, opus); null bila tidak diketahui. */
    val codec: String?,
    /** Tinggi piksel; 0 untuk audio. */
    val height: Int,
    val width: Int,
    val fps: Int,
    val bitrateKbps: Int,
    val client: StreamClient
)

/** Hasil pemilihan: pasangan [video]+[audio] terpisah, atau satu stream [combined] (video+audio). */
internal data class StreamChoice(
    val video: MediaCandidate?,
    val audio: MediaCandidate?,
    val combined: MediaCandidate?
)

/**
 * Memilih stream terbaik untuk perangkat Android 8: tidak AV1 (tanpa dekoder perangkat keras),
 * tinggi maksimum mengikuti selector ytdl-format, H.264 lebih disukai daripada VP9 pada tinggi
 * yang sama, dan audio AAC (m4a) lebih disukai. Audio dipilih dari klien yang sama dengan video
 * agar satu User-Agent berlaku untuk kedua stream.
 */
internal object StreamPicker {

    const val DEFAULT_MAX_HEIGHT = 1080
    private const val MIN_HEIGHT_LIMIT = 144
    private const val MAX_HEIGHT_LIMIT = 4320

    private val HEIGHT_LIMIT = Regex("""height\s*<=\s*\??\s*(\d{3,4})""")

    /** Batas tinggi dari selector ytdl-format (mis. "height<=?720"); bawaan 1080 bila tidak ada. */
    fun maxHeight(selector: String?): Int {
        val value = selector?.let { HEIGHT_LIMIT.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        return (value ?: DEFAULT_MAX_HEIGHT).coerceIn(MIN_HEIGHT_LIMIT, MAX_HEIGHT_LIMIT)
    }

    fun pick(
        videoOnly: List<MediaCandidate>,
        audio: List<MediaCandidate>,
        progressive: List<MediaCandidate>,
        maxHeight: Int
    ): StreamChoice? {
        val video = bestVideo(videoOnly, maxHeight)
        if (video != null) {
            val track = bestAudio(audio, video.client)
            if (track != null) return StreamChoice(video, track, null)
            val muxed = bestVideo(progressive, maxHeight)
            if (muxed != null) return StreamChoice(null, null, muxed)
            return StreamChoice(video, null, null)
        }
        val muxed = bestVideo(progressive, maxHeight) ?: return null
        return StreamChoice(null, null, muxed)
    }

    private fun isAv1(candidate: MediaCandidate): Boolean =
        candidate.codec?.lowercase()?.startsWith("av01") == true

    private fun isAvc(candidate: MediaCandidate): Boolean =
        candidate.codec?.lowercase()?.startsWith("avc1") == true

    /** Tertinggi yang tidak melebihi [maxHeight]; bila semua melebihinya, yang terendah. */
    private fun bestVideo(candidates: List<MediaCandidate>, maxHeight: Int): MediaCandidate? {
        val usable = candidates.filter { it.height > 0 && it.url.isNotEmpty() && !isAv1(it) }
        if (usable.isEmpty()) return null
        val within = usable.filter { it.height <= maxHeight }
        if (within.isEmpty()) return usable.minWith(compareBy<MediaCandidate> { it.height }.thenBy { it.bitrateKbps })
        return within.maxWith(
            compareBy<MediaCandidate> { it.height }
                .thenBy { if (isAvc(it)) 1 else 0 }
                .thenBy { it.bitrateKbps }
        )
    }

    private fun bestAudio(candidates: List<MediaCandidate>, client: StreamClient): MediaCandidate? {
        val usable = candidates.filter { it.url.isNotEmpty() }
        val sameClient = usable.filter { it.client == client }
        val pool = sameClient.ifEmpty { usable }
        return pool.maxWithOrNull(
            compareBy<MediaCandidate> { if (it.ext == "m4a") 1 else 0 }.thenBy { it.bitrateKbps }
        )
    }
}
