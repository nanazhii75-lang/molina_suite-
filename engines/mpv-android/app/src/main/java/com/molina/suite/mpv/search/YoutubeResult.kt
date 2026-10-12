package com.molina.suite.mpv.search

/** Satu hasil pencarian video; kolom opsional bernilai null bila sumber tidak menyediakannya. */
data class YoutubeResult(
    val id: String,
    val title: String,
    val channel: String,
    val viewCount: Long?,
    val durationSeconds: Long?,
    val publishedAtMillis: Long?,
    val thumbnailUrl: String,
    val watchUrl: String
)
