package com.molina.suite.feature.mpv

import com.molina.suite.mpv.search.YoutubeResult
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Mengubah keluaran `yt-dlp --dump-json` (satu objek JSON per baris) menjadi
 * [YoutubeResult]. Semua kolom opsional ditoleransi hilang atau null.
 */
internal object YtdlpOutputParser {

    private const val WATCH_BASE = "https://www.youtube.com/watch?v="
    private const val THUMB_BASE = "https://i.ytimg.com/vi/"
    private const val MAX_THUMB_WIDTH = 480
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

    fun parse(output: String, limit: Int): List<YoutubeResult> {
        val results = ArrayList<YoutubeResult>()
        for (line in output.lineSequence()) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("{")) continue
            val item = try {
                parseObject(JSONObject(trimmed))
            } catch (e: JSONException) {
                null
            }
            if (item != null) results.add(item)
        }
        val unique = results.distinctBy { it.id }
        // Urut ulang hanya bila setiap hasil punya waktu unggah; selain itu pertahankan
        // urutan dari yt-dlp (urutan relevansi).
        val ordered = if (unique.all { it.publishedAtMillis != null }) {
            unique.sortedByDescending { it.publishedAtMillis }
        } else {
            unique
        }
        return ordered.take(limit)
    }

    private fun parseObject(o: JSONObject): YoutubeResult? {
        val id = o.str("id") ?: return null
        if (!VIDEO_ID.matches(id)) return null // buang playlist/kanal
        val title = o.str("title") ?: return null
        val url = o.str("url")
        val published = o.long("timestamp")?.times(1000L) ?: uploadDateMillis(o.str("upload_date"))
        return YoutubeResult(
            id = id,
            title = title,
            channel = o.str("channel") ?: o.str("uploader") ?: "",
            viewCount = o.long("view_count"),
            durationSeconds = o.long("duration"),
            publishedAtMillis = published,
            thumbnailUrl = pickThumbnail(o, id),
            watchUrl = if (url != null && url.startsWith("https://")) url else WATCH_BASE + id
        )
    }

    private fun pickThumbnail(o: JSONObject, id: String): String {
        var bestUrl: String? = null
        var bestWidth = 0
        val array = o.optJSONArray("thumbnails")
        if (array != null) {
            for (i in 0 until array.length()) {
                val t = array.optJSONObject(i) ?: continue
                val url = t.str("url") ?: continue
                if (!url.startsWith("https://")) continue
                val width = t.long("width")?.toInt() ?: 0
                if (width in 1..MAX_THUMB_WIDTH && width > bestWidth) {
                    bestWidth = width
                    bestUrl = url
                }
            }
        }
        return bestUrl
            ?: o.str("thumbnail")?.takeIf { it.startsWith("https://") }
            ?: (THUMB_BASE + id + "/mqdefault.jpg")
    }

    private fun uploadDateMillis(raw: String?): Long? {
        if (raw == null || raw.length != 8) return null
        return try {
            val format = SimpleDateFormat("yyyyMMdd", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.isLenient = false
            format.parse(raw)?.time
        } catch (e: ParseException) {
            null
        }
    }

    // org.json mengembalikan teks "null" untuk JSON null pada optString, jadi cek isNull dulu.
    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key, "").trim().ifEmpty { null }

    private fun JSONObject.long(key: String): Long? {
        if (isNull(key)) return null
        val value = optDouble(key, Double.NaN)
        return if (value.isNaN()) null else value.toLong()
    }
}
