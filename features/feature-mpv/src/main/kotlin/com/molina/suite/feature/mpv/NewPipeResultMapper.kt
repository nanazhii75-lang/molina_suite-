package com.molina.suite.feature.mpv

import com.molina.suite.mpv.search.YoutubeResult
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** Mengubah [StreamInfoItem] Extractor menjadi [YoutubeResult] yang dipakai UI mpv. */
internal object NewPipeResultMapper {

    private const val WATCH_BASE = "https://www.youtube.com/watch?v="
    private const val THUMB_BASE = "https://i.ytimg.com/vi/"
    private const val MAX_THUMB_WIDTH = 480
    private val VIDEO_ID = Regex("""[?&]v=([A-Za-z0-9_-]{11})(?:&|$)""")

    /** Null bila item tidak punya ID video atau judul yang valid. */
    fun map(item: StreamInfoItem): YoutubeResult? {
        val id = VIDEO_ID.find(item.url.orEmpty())?.groupValues?.get(1) ?: return null
        val title = item.name?.trim().orEmpty()
        if (title.isEmpty()) return null
        val live = item.streamType == StreamType.LIVE_STREAM
        return YoutubeResult(
            id = id,
            title = title,
            channel = item.uploaderName?.trim().orEmpty(),
            viewCount = item.viewCount.takeIf { it >= 0L },
            // Durasi null membuat UI menampilkan lencana LIVE.
            durationSeconds = if (live) null else item.duration.coerceAtLeast(0L),
            publishedAtMillis = item.uploadDate?.instant?.toEpochMilli(),
            thumbnailUrl = pickThumbnail(item.thumbnails, id),
            watchUrl = WATCH_BASE + id
        )
    }

    private fun pickThumbnail(images: List<Image>?, id: String): String {
        var bestUrl: String? = null
        var bestWidth = 0
        if (images != null) {
            for (image in images) {
                val url = image.url
                val width = image.width
                if (url.startsWith("https://") && width in 1..MAX_THUMB_WIDTH && width > bestWidth) {
                    bestWidth = width
                    bestUrl = url
                }
            }
        }
        return bestUrl ?: (THUMB_BASE + id + "/mqdefault.jpg")
    }
}
