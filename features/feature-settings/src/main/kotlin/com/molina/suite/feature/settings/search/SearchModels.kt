package com.molina.suite.feature.settings.search

/** Satu hasil pencarian yang sudah dibersihkan dari pembungkus pelacak dan parameter pelacakan. */
data class SearchResult(
    val title: String,
    val url: String,
    val host: String,
    val snippet: String
) {
    /** False untuk tautan http polos; layar hasil menandainya sebagai tidak aman. */
    val secure: Boolean get() = url.startsWith("https://", ignoreCase = true)
}

enum class SafeSearch(val code: String) {
    STRICT("1"),
    MODERATE("-1"),
    OFF("-2")
}

/** Region "wt-wt" berarti tanpa wilayah, sehingga permintaan tidak membawa sinyal lokasi. */
data class SearchOptions(
    val safeSearch: SafeSearch = SafeSearch.MODERATE,
    val region: String = "wt-wt"
)

enum class SearchFailure {
    NETWORK,
    TIMEOUT,
    SERVER,
    TOO_LARGE,
    UNSAFE_REDIRECT,
    UNEXPECTED_FORMAT
}

sealed interface SearchOutcome {
    /** [blocked] = jumlah iklan dan pelacak yang dibuang dari halaman hasil. */
    data class Success(val results: List<SearchResult>, val blocked: Int) : SearchOutcome

    /** Tidak ada hasil, atau semuanya iklan/pelacak yang dibuang. */
    data class Empty(val blocked: Int) : SearchOutcome

    /** Penyedia meminta verifikasi (anti-bot) atau membatasi permintaan. */
    data object Blocked : SearchOutcome

    data object InvalidQuery : SearchOutcome

    data class Failure(val reason: SearchFailure, val httpCode: Int = 0) : SearchOutcome
}

/** Kontrak mesin pencari privat; UI hanya mengenal antarmuka ini. */
interface PrivateSearch {
    suspend fun search(query: String, options: SearchOptions = SearchOptions()): SearchOutcome
}
