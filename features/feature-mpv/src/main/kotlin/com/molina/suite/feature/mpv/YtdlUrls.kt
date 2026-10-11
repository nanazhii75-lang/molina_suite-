package com.molina.suite.feature.mpv

import android.net.Uri

/** Membedakan tautan situs video (butuh yt-dlp) dari tautan langsung ke berkas media. */
internal object YtdlUrls {

    private val directExtensions = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "flv",
        "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav",
        "m3u8", "mpd"
    )

    /** True bila [url] adalah http(s) yang bukan berkas media langsung. */
    fun needsYtdl(url: String): Boolean {
        val uri = Uri.parse(url.trim())
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return extension !in directExtensions
    }
}
