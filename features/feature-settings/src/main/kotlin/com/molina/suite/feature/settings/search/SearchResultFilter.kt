package com.molina.suite.feature.settings.search

/** Hasil penyaringan HTTPS-only: [kept] ditampilkan, [droppedHttp] jumlah tautan http yang dibuang. */
data class FilteredResults(val kept: List<SearchResult>, val droppedHttp: Int)

/** Saringan murni (tanpa I/O): membuang tautan http polos bila HTTPS-only aktif. */
object SearchResultFilter {

    fun apply(results: List<SearchResult>, httpsOnly: Boolean): FilteredResults {
        if (!httpsOnly) return FilteredResults(results, 0)
        val kept = results.filter { it.secure }
        return FilteredResults(kept, results.size - kept.size)
    }
}
