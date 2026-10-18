package com.molina.suite.feature.settings.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mesin pencari privat berbasis halaman HTML DuckDuckGo. Satu-satunya host yang dihubungi adalah
 * html.duckduckgo.com lewat HTTPS; tidak ada cookie, riwayat, atau data yang disimpan.
 */
class DuckDuckGoSearch(
    private val fetcher: PageFetcher = HardenedPageFetcher(setOf(HOST))
) : PrivateSearch {

    override suspend fun search(query: String, options: SearchOptions): SearchOutcome {
        val text = QueryText.normalize(query) ?: return SearchOutcome.InvalidQuery
        val form = linkedMapOf(
            "q" to text,
            "b" to "",
            "kl" to options.region,
            "kp" to options.safeSearch.code
        )
        return when (val fetched = fetcher.postForm(ENDPOINT, form)) {
            is FetchResult.Error ->
                if (fetched.httpCode in CHALLENGE_CODES) {
                    SearchOutcome.Blocked
                } else {
                    SearchOutcome.Failure(fetched.reason, fetched.httpCode)
                }
            is FetchResult.Page -> withContext(Dispatchers.Default) { interpret(fetched.body) }
        }
    }

    internal fun interpret(html: String): SearchOutcome {
        if (DuckDuckGoParser.isChallenge(html)) return SearchOutcome.Blocked
        val page = DuckDuckGoParser.parse(html)
        return when {
            page.results.isNotEmpty() -> SearchOutcome.Success(page.results, page.blocked)
            page.sawResultMarkup || DuckDuckGoParser.isNoResultsPage(html) -> SearchOutcome.Empty(page.blocked)
            else -> SearchOutcome.Failure(SearchFailure.UNEXPECTED_FORMAT)
        }
    }

    private companion object {
        const val HOST = "html.duckduckgo.com"
        const val ENDPOINT = "https://$HOST/html/"
        val CHALLENGE_CODES = setOf(202, 403, 418, 429)
    }
}
