package com.molina.suite.mpv.search

/**
 * Titik pertemuan longgar antara UI mpv dan penyedia pencarian: modul fitur memasang
 * implementasi saat startup, UI mengambilnya lewat [current].
 */
object YoutubeSearch {

    @Volatile
    private var source: YoutubeSearchSource? = null

    fun install(newSource: YoutubeSearchSource) {
        source = newSource
    }

    @Throws(YoutubeSearchException::class)
    fun current(): YoutubeSearchSource = source ?: throw YoutubeSearchException(
        YoutubeSearchException.Reason.UNAVAILABLE,
        "Penyedia pencarian YouTube belum dipasang"
    )
}
