package com.molina.suite.mpv.search

/** Kontrak penyedia pencarian YouTube; UI mpv hanya mengenal antarmuka ini. */
interface YoutubeSearchSource {

    /**
     * Mencari video dan mengembalikan paling banyak [limit] hasil. Urutan terbaru ke
     * terlama hanya bila waktu unggah tersedia; selain itu urutan relevansi.
     * Memblokir thread pemanggil: panggil dari thread latar.
     */
    @Throws(YoutubeSearchException::class)
    fun search(query: String, limit: Int): List<YoutubeResult>
}

/** Kegagalan pencarian; [reason] membedakan pesan yang perlu ditampilkan UI. */
class YoutubeSearchException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    enum class Reason { UNAVAILABLE, TIMEOUT, FAILED }
}
