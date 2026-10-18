package com.molina.suite.feature.settings.search

sealed interface FetchResult {
    data class Page(val body: String) : FetchResult
    data class Error(val reason: SearchFailure, val httpCode: Int = 0) : FetchResult
}

/** Pengambil halaman web; dipisah dari mesin pencari agar mesin bisa diuji tanpa jaringan. */
interface PageFetcher {
    suspend fun postForm(url: String, form: Map<String, String>): FetchResult
}
