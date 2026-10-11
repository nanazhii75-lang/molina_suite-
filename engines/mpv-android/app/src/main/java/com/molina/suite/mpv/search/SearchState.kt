package com.molina.suite.mpv.search

/** Keadaan pencarian YouTube yang ditampilkan panel hasil. */
sealed class SearchState {

    /** Belum pernah mencari. */
    object Idle : SearchState()

    /** Sedang mengambil halaman [page]; [done] dari [total] hasil halaman itu sudah diambil. */
    data class Loading(
        val query: String,
        val done: Int,
        val total: Int,
        val page: Int = 1
    ) : SearchState()

    /**
     * Satu halaman hasil ([page], mulai 1), terurut terbaru ke terlama. [hasNext] true bila
     * halaman berikutnya mungkin tersedia; halaman sebelumnya ada bila [page] > 1.
     */
    data class Results(
        val query: String,
        val items: List<YoutubeResult>,
        val page: Int = 1,
        val hasNext: Boolean = false
    ) : SearchState()

    /** Pencarian gagal; [detail] adalah ringkasan teknis untuk ditampilkan apa adanya. */
    data class Failed(
        val query: String,
        val reason: YoutubeSearchException.Reason,
        val detail: String
    ) : SearchState()
}
