package com.molina.suite.mpv.search

/** Keadaan pencarian YouTube yang ditampilkan panel hasil. */
sealed class SearchState {

    /** Belum pernah mencari. */
    object Idle : SearchState()

    /** Sedang mencari; [done] dari [total] hasil sudah diambil. */
    data class Loading(val query: String, val done: Int, val total: Int) : SearchState()

    /** Pencarian selesai; [items] terurut terbaru ke terlama. */
    data class Results(val query: String, val items: List<YoutubeResult>) : SearchState()

    /** Pencarian gagal; [detail] adalah ringkasan teknis untuk ditampilkan apa adanya. */
    data class Failed(
        val query: String,
        val reason: YoutubeSearchException.Reason,
        val detail: String
    ) : SearchState()
}
