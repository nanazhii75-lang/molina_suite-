package com.molina.suite.feature.settings.search

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Keadaan layar Search. Semuanya hanya di memori; tidak ada yang ditulis ke disk. */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState

    /**
     * [droppedHttp] = tautan http yang dibuang oleh HTTPS-only. [blocked] = iklan dan pelacak yang
     * dibuang mesin. [showingHttp] = true bila http ditampilkan sekali pakai.
     */
    data class Results(
        val items: List<SearchResult>,
        val blocked: Int,
        val droppedHttp: Int,
        val showingHttp: Boolean
    ) : SearchUiState

    data class Empty(val blocked: Int, val droppedHttp: Int) : SearchUiState
    data object Blocked : SearchUiState
    data object InvalidQuery : SearchUiState
    data class Failed(val reason: SearchFailure, val httpCode: Int) : SearchUiState
}

/** Pilihan pengguna di layar Search; HTTPS-only aktif secara bawaan. */
data class SearchSettings(
    val httpsOnly: Boolean = true,
    val safeSearch: SafeSearch = SafeSearch.MODERATE
)

/**
 * Menyimpan hasil selama layar diputar atau tab berpindah, dan melepasnya saat layar Search ditutup.
 * Tidak ada riwayat, cache, atau berkas: kata kunci terakhir hanya hidup selama ViewModel hidup.
 */
class SearchViewModel(
    private val engine: PrivateSearch,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val onRelease: () -> Unit = {}
) : ViewModel() {

    private val mutableState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

    private val mutableSettings = MutableStateFlow(SearchSettings())
    val settings: StateFlow<SearchSettings> = mutableSettings.asStateFlow()

    private var job: Job? = null
    private var lastQuery: String = ""
    private var lastResults: List<SearchResult> = emptyList()
    private var lastBlocked: Int = 0
    private var showHttpOnce: Boolean = false

    /** Kata kunci terakhir, agar kolom isian bisa dipulihkan setelah layar diputar. */
    val query: String get() = lastQuery

    fun search(rawQuery: String) {
        val text = rawQuery.trim()
        job?.cancel()
        lastQuery = text
        showHttpOnce = false
        mutableState.value = SearchUiState.Loading
        val options = SearchOptions(safeSearch = mutableSettings.value.safeSearch)
        job = scope.launch {
            val outcome = try {
                engine.search(text, options)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchOutcome.Failure(SearchFailure.NETWORK)
            }
            mutableState.value = interpret(outcome)
        }
    }

    fun retry() {
        if (lastQuery.isNotEmpty()) search(lastQuery)
    }

    fun setHttpsOnly(enabled: Boolean) {
        if (mutableSettings.value.httpsOnly == enabled) return
        mutableSettings.value = mutableSettings.value.copy(httpsOnly = enabled)
        showHttpOnce = false
        republish()
    }

    fun setSafeSearch(value: SafeSearch) {
        mutableSettings.value = mutableSettings.value.copy(safeSearch = value)
    }

    /** Menampilkan tautan http untuk hasil yang sedang tampil saja, tanpa mengubah pengaturan. */
    fun showHttpOnce() {
        if (state.value !is SearchUiState.Results && state.value !is SearchUiState.Empty) return
        showHttpOnce = true
        republish()
    }

    private fun republish() {
        val current = state.value
        if (current is SearchUiState.Results || current is SearchUiState.Empty) {
            mutableState.value = fromResults(lastResults, lastBlocked)
        }
    }

    private fun interpret(outcome: SearchOutcome): SearchUiState = when (outcome) {
        is SearchOutcome.Success -> {
            lastResults = outcome.results
            lastBlocked = outcome.blocked
            fromResults(outcome.results, outcome.blocked)
        }
        is SearchOutcome.Empty -> {
            lastResults = emptyList()
            lastBlocked = outcome.blocked
            SearchUiState.Empty(outcome.blocked, 0)
        }
        SearchOutcome.Blocked -> SearchUiState.Blocked
        SearchOutcome.InvalidQuery -> SearchUiState.InvalidQuery
        is SearchOutcome.Failure -> SearchUiState.Failed(outcome.reason, outcome.httpCode)
    }

    private fun fromResults(results: List<SearchResult>, blocked: Int): SearchUiState {
        val httpsOnly = mutableSettings.value.httpsOnly && !showHttpOnce
        val filtered = SearchResultFilter.apply(results, httpsOnly)
        return if (filtered.kept.isEmpty()) {
            SearchUiState.Empty(blocked, filtered.droppedHttp)
        } else {
            SearchUiState.Results(filtered.kept, blocked, filtered.droppedHttp, showHttpOnce && results.any { !it.secure })
        }
    }

    override fun onCleared() {
        scope.cancel()
        runCatching { onRelease() }
        job = null
        lastResults = emptyList()
        super.onCleared()
    }
}
