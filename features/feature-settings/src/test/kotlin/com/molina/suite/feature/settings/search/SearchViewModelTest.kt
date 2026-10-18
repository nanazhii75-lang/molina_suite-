package com.molina.suite.feature.settings.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchViewModelTest {

    private class FakeEngine(var outcome: SearchOutcome) : PrivateSearch {
        var calls = 0
        var lastQuery = ""
        var lastOptions = SearchOptions()
        var failure: Exception? = null

        override suspend fun search(query: String, options: SearchOptions): SearchOutcome {
            calls++
            lastQuery = query
            lastOptions = options
            failure?.let { throw it }
            return outcome
        }
    }

    private fun result(url: String) = SearchResult("t", url, "h", "s")

    private val mixed = SearchOutcome.Success(
        listOf(result("https://a.example/"), result("http://b.example/")),
        blocked = 3
    )

    private fun model(engine: PrivateSearch) =
        SearchViewModel(engine, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun httpsOnlyIsOnByDefaultAndDropsHttpResults() {
        val vm = model(FakeEngine(mixed))
        vm.search("kucing")
        val state = vm.state.value as SearchUiState.Results
        assertEquals(listOf("https://a.example/"), state.items.map { it.url })
        assertEquals(1, state.droppedHttp)
        assertEquals(3, state.blocked)
        assertTrue(!state.showingHttp)
    }

    @Test
    fun showHttpOnceRevealsThemWithoutNewRequestAndResetsOnNextSearch() {
        val engine = FakeEngine(mixed)
        val vm = model(engine)
        vm.search("kucing")
        vm.showHttpOnce()
        val shown = vm.state.value as SearchUiState.Results
        assertEquals(2, shown.items.size)
        assertTrue(shown.showingHttp)
        assertEquals(1, engine.calls)
        assertTrue(vm.settings.value.httpsOnly)

        vm.search("kucing")
        assertEquals(1, (vm.state.value as SearchUiState.Results).items.size)
    }

    @Test
    fun turningHttpsOnlyOffShowsEverything() {
        val vm = model(FakeEngine(mixed))
        vm.search("kucing")
        vm.setHttpsOnly(false)
        assertEquals(2, (vm.state.value as SearchUiState.Results).items.size)
        vm.setHttpsOnly(true)
        assertEquals(1, (vm.state.value as SearchUiState.Results).items.size)
    }

    @Test
    fun onlyHttpResultsBecomeEmptyWithDroppedCount() {
        val engine = FakeEngine(SearchOutcome.Success(listOf(result("http://x.example/")), blocked = 0))
        val vm = model(engine)
        vm.search("x")
        val state = vm.state.value as SearchUiState.Empty
        assertEquals(1, state.droppedHttp)
    }

    @Test
    fun failureCanBeRetriedWithTheSameQuery() {
        val engine = FakeEngine(SearchOutcome.Failure(SearchFailure.TIMEOUT))
        val vm = model(engine)
        vm.search("  kucing ")
        assertEquals(SearchUiState.Failed(SearchFailure.TIMEOUT, 0), vm.state.value)
        engine.outcome = mixed
        vm.retry()
        assertEquals(2, engine.calls)
        assertEquals("kucing", engine.lastQuery)
        assertTrue(vm.state.value is SearchUiState.Results)
    }

    @Test
    fun unexpectedExceptionBecomesNetworkFailureWithoutLeakingMessage() {
        val engine = FakeEngine(mixed).also { it.failure = IllegalStateException("kata kunci rahasia") }
        val vm = model(engine)
        vm.search("rahasia")
        assertEquals(SearchUiState.Failed(SearchFailure.NETWORK, 0), vm.state.value)
    }

    @Test
    fun safeSearchChoiceIsPassedToTheEngine() {
        val engine = FakeEngine(mixed)
        val vm = model(engine)
        vm.setSafeSearch(SafeSearch.STRICT)
        vm.search("x")
        assertEquals(SafeSearch.STRICT, engine.lastOptions.safeSearch)
    }

    @Test
    fun blockedAndInvalidAreForwarded() {
        val engine = FakeEngine(SearchOutcome.Blocked)
        val vm = model(engine)
        vm.search("x")
        assertEquals(SearchUiState.Blocked, vm.state.value)
        engine.outcome = SearchOutcome.InvalidQuery
        vm.search("")
        assertEquals(SearchUiState.InvalidQuery, vm.state.value)
    }

    @Test
    fun retryWithoutAnyQueryDoesNothing() {
        val engine = FakeEngine(mixed)
        val vm = model(engine)
        vm.retry()
        assertEquals(0, engine.calls)
        assertEquals(SearchUiState.Idle, vm.state.value)
    }
}
