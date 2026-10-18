package com.molina.suite.feature.settings.search

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuckDuckGoSearchTest {

    private class FakeFetcher(var result: FetchResult) : PageFetcher {
        var calls = 0
        var lastUrl = ""
        var lastForm: Map<String, String> = emptyMap()

        override suspend fun postForm(url: String, form: Map<String, String>): FetchResult {
            calls++
            lastUrl = url
            lastForm = form
            return result
        }
    }

    private val okPage = """
        <div class="result results_links web-result">
        <h2><a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.org%2F">Contoh</a></h2>
        <a class="result__snippet" href="x">Isi</a></div>
    """.trimIndent()

    @Test
    fun blankQueryNeverReachesTheNetwork() = runBlocking {
        val fetcher = FakeFetcher(FetchResult.Page(okPage))
        assertEquals(SearchOutcome.InvalidQuery, DuckDuckGoSearch(fetcher).search("   "))
        assertEquals(0, fetcher.calls)
    }

    @Test
    fun sendsOnlyTheNormalizedQueryToTheFixedEndpoint() = runBlocking {
        val fetcher = FakeFetcher(FetchResult.Page(okPage))
        DuckDuckGoSearch(fetcher).search("  kucing   lucu ", SearchOptions(SafeSearch.STRICT))
        assertEquals("https://html.duckduckgo.com/html/", fetcher.lastUrl)
        assertEquals("kucing lucu", fetcher.lastForm["q"])
        assertEquals("1", fetcher.lastForm["kp"])
        assertEquals("wt-wt", fetcher.lastForm["kl"])
    }

    @Test
    fun returnsCleanResults() = runBlocking {
        val outcome = DuckDuckGoSearch(FakeFetcher(FetchResult.Page(okPage))).search("contoh")
        val success = outcome as SearchOutcome.Success
        assertEquals("https://example.org/", success.results.single().url)
        assertEquals(0, success.blocked)
    }

    @Test
    fun challengeStatusCodesBecomeBlocked() = runBlocking {
        for (code in listOf(202, 403, 429)) {
            val fetcher = FakeFetcher(FetchResult.Error(SearchFailure.SERVER, code))
            assertEquals(SearchOutcome.Blocked, DuckDuckGoSearch(fetcher).search("a"))
        }
    }

    @Test
    fun challengePageBecomesBlocked() {
        val engine = DuckDuckGoSearch(FakeFetcher(FetchResult.Page("")))
        assertEquals(SearchOutcome.Blocked, engine.interpret("<div class=\"anomaly-modal\"></div>"))
    }

    @Test
    fun otherErrorsKeepTheirReason() = runBlocking {
        val fetcher = FakeFetcher(FetchResult.Error(SearchFailure.TIMEOUT))
        val outcome = DuckDuckGoSearch(fetcher).search("a")
        assertEquals(SearchOutcome.Failure(SearchFailure.TIMEOUT, 0), outcome)
    }

    @Test
    fun serverErrorKeepsStatusCode() = runBlocking {
        val fetcher = FakeFetcher(FetchResult.Error(SearchFailure.SERVER, 500))
        assertEquals(SearchOutcome.Failure(SearchFailure.SERVER, 500), DuckDuckGoSearch(fetcher).search("a"))
    }

    @Test
    fun noResultsPageIsEmpty() {
        val engine = DuckDuckGoSearch(FakeFetcher(FetchResult.Page("")))
        assertTrue(engine.interpret("<div class=\"no-results\">No results.</div>") is SearchOutcome.Empty)
    }

    @Test
    fun allResultsFilteredIsEmptyWithBlockedCount() {
        val html = """<div class="result result--ad"><a class="result__a" href="//duckduckgo.com/y.js?ad_domain=x">Iklan</a></div>"""
        val outcome = DuckDuckGoSearch(FakeFetcher(FetchResult.Page(""))).interpret(html)
        assertEquals(SearchOutcome.Empty(1), outcome)
    }

    @Test
    fun unknownPageIsUnexpectedFormat() {
        val engine = DuckDuckGoSearch(FakeFetcher(FetchResult.Page("")))
        assertEquals(SearchOutcome.Failure(SearchFailure.UNEXPECTED_FORMAT, 0), engine.interpret("<html>apa saja</html>"))
    }
}
