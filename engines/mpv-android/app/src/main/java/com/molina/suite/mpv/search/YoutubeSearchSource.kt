package com.molina.suite.mpv.search

/** Kontrak penyedia pencarian YouTube; UI mpv hanya mengenal antarmuka ini. */
interface YoutubeSearchSource {

    /**
     * Mencari video dan mengembalikan paling banyak [limit] hasil mulai dari urutan ke-[offset]
     * (basis nol). Urutan sumber adalah terbaru ke terlama menurut waktu unggah; hasil tanpa
     * waktu unggah ditaruh paling bawah dalam halaman yang sama.
     *
     * Memblokir thread pemanggil dan butuh puluhan detik: panggil dari thread latar.
     * Menginterupsi thread pemanggil membatalkan pencarian. [onProgress] dipanggil dari
     * thread latar lain setiap satu hasil selesai diambil, dengan jumlah hasil sejauh ini.
     */
    @Throws(YoutubeSearchException::class)
    fun search(
        query: String,
        offset: Int,
        limit: Int,
        onProgress: (Int) -> Unit = {}
    ): List<YoutubeResult>
}

/** Kegagalan pencarian; [reason] membedakan pesan yang perlu ditampilkan UI. */
class YoutubeSearchException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    enum class Reason { UNAVAILABLE, TIMEOUT, FAILED }
}
