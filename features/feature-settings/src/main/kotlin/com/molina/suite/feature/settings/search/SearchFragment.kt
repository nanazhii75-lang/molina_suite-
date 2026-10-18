package com.molina.suite.feature.settings.search

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.molina.suite.feature.settings.R
import kotlinx.coroutines.launch

/**
 * Layar Cari Privat. Semua logika ada di [SearchViewModel]; fragment ini hanya menampilkan keadaan
 * dan meneruskan aksi pengguna. Tidak ada riwayat dan tidak ada penyimpanan ke disk.
 */
class SearchFragment : Fragment(R.layout.fragment_search) {

    private lateinit var viewModel: SearchViewModel
    private val resultAdapter = SearchAdapter { result -> showActions(result) }

    private var input: EditText? = null
    private var summary: TextView? = null
    private var showHttp: MaterialButton? = null
    private var progress: ProgressBar? = null
    private var messageBox: View? = null
    private var message: TextView? = null
    private var retry: MaterialButton? = null
    private var chipHttps: TextView? = null
    private var chipSafe: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this, factory)[SearchViewModel::class.java]
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val editText = view.findViewById<EditText>(R.id.search_input)
        input = editText
        summary = view.findViewById(R.id.search_summary)
        showHttp = view.findViewById(R.id.search_show_http)
        progress = view.findViewById(R.id.search_progress)
        messageBox = view.findViewById(R.id.search_message_box)
        message = view.findViewById(R.id.search_message)
        retry = view.findViewById(R.id.search_retry)
        chipHttps = view.findViewById(R.id.search_chip_https)
        chipSafe = view.findViewById(R.id.search_chip_safe)

        val list = view.findViewById<RecyclerView>(R.id.search_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = resultAdapter

        if (editText.text.isNullOrEmpty()) editText.setText(viewModel.query)
        editText.setOnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                submit()
                true
            } else {
                false
            }
        }
        view.findViewById<View>(R.id.search_go).setOnClickListener { submit() }
        retry?.setOnClickListener { viewModel.retry() }
        showHttp?.setOnClickListener { viewModel.showHttpOnce() }
        chipHttps?.setOnClickListener { viewModel.setHttpsOnly(!viewModel.settings.value.httpsOnly) }
        chipSafe?.setOnClickListener { viewModel.setSafeSearch(nextSafeSearch(viewModel.settings.value.safeSearch)) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.settings.collect { renderChips(it) } }
                launch { viewModel.state.collect { render(it) } }
            }
        }
    }

    private fun submit() {
        val text = input?.text?.toString().orEmpty()
        hideKeyboard()
        viewModel.search(text)
    }

    private fun hideKeyboard() {
        val target = input ?: return
        val manager = requireContext().getSystemService(InputMethodManager::class.java)
        manager?.hideSoftInputFromWindow(target.windowToken, 0)
    }

    private fun renderChips(settings: SearchSettings) {
        chipHttps?.apply {
            setText(if (settings.httpsOnly) R.string.search_chip_https_on else R.string.search_chip_https_off)
            setBackgroundResource(if (settings.httpsOnly) R.drawable.ms_bg_chip_on else R.drawable.ms_bg_chip_off)
        }
        chipSafe?.apply {
            setText(
                when (settings.safeSearch) {
                    SafeSearch.STRICT -> R.string.search_chip_safe_strict
                    SafeSearch.MODERATE -> R.string.search_chip_safe_moderate
                    SafeSearch.OFF -> R.string.search_chip_safe_off
                }
            )
            setBackgroundResource(
                if (settings.safeSearch == SafeSearch.OFF) R.drawable.ms_bg_chip_off else R.drawable.ms_bg_chip_on
            )
        }
    }

    private fun render(state: SearchUiState) {
        progress?.visibility = if (state is SearchUiState.Loading) View.VISIBLE else View.GONE
        retry?.visibility = View.GONE
        showHttp?.visibility = View.GONE
        when (state) {
            SearchUiState.Idle -> {
                resultAdapter.submitList(emptyList())
                hideSummary()
                hideMessage()
            }
            SearchUiState.Loading -> {
                hideSummary()
                showMessage(getString(R.string.search_loading), canRetry = false)
            }
            is SearchUiState.Results -> {
                resultAdapter.submitList(state.items)
                hideMessage()
                showSummary(state.items.size, state.blocked, state.droppedHttp)
                showHttp?.visibility = if (state.droppedHttp > 0) View.VISIBLE else View.GONE
                if (state.showingHttp) summary?.append("\n" + getString(R.string.search_http_shown_once))
            }
            is SearchUiState.Empty -> {
                resultAdapter.submitList(emptyList())
                showSummary(0, state.blocked, state.droppedHttp)
                val text = if (state.droppedHttp > 0) {
                    getString(R.string.search_empty_http_dropped, state.droppedHttp)
                } else {
                    getString(R.string.search_empty)
                }
                showMessage(text, canRetry = false)
                showHttp?.visibility = if (state.droppedHttp > 0) View.VISIBLE else View.GONE
            }
            SearchUiState.Blocked -> failure(getString(R.string.search_blocked))
            SearchUiState.InvalidQuery -> {
                resultAdapter.submitList(emptyList())
                hideSummary()
                showMessage(getString(R.string.search_invalid), canRetry = false)
            }
            is SearchUiState.Failed -> failure(failureText(state), canRetry = isRetryable(state.reason))
        }
    }

    private fun failure(text: String, canRetry: Boolean = true) {
        resultAdapter.submitList(emptyList())
        hideSummary()
        showMessage(text, canRetry = canRetry)
    }

    /** Coba lagi hanya untuk galat sementara; sertifikat salah atau pengalihan tidak aman tidak diulang. */
    private fun isRetryable(reason: SearchFailure): Boolean = when (reason) {
        SearchFailure.NETWORK, SearchFailure.TIMEOUT, SearchFailure.SERVER, SearchFailure.DNS_FAILED -> true
        else -> false
    }

    private fun failureText(state: SearchUiState.Failed): String = when (state.reason) {
        SearchFailure.NETWORK -> getString(R.string.search_failed_network)
        SearchFailure.TIMEOUT -> getString(R.string.search_failed_timeout)
        SearchFailure.SERVER -> getString(R.string.search_failed_server, state.httpCode)
        SearchFailure.TOO_LARGE -> getString(R.string.search_failed_large)
        SearchFailure.UNSAFE_REDIRECT -> getString(R.string.search_failed_redirect)
        SearchFailure.UNEXPECTED_FORMAT -> getString(R.string.search_failed_format)
        SearchFailure.CERTIFICATE_MISMATCH -> getString(R.string.search_failed_certificate)
        SearchFailure.DNS_FAILED -> getString(R.string.search_failed_dns)
    }

    private fun showSummary(count: Int, blocked: Int, droppedHttp: Int) {
        summary?.apply {
            text = if (droppedHttp > 0) {
                getString(R.string.search_summary_http_dropped, count, blocked, droppedHttp)
            } else {
                getString(R.string.search_summary, count, blocked)
            }
            visibility = View.VISIBLE
        }
    }

    private fun hideSummary() {
        summary?.visibility = View.GONE
    }

    private fun showMessage(text: String, canRetry: Boolean) {
        message?.text = text
        messageBox?.visibility = View.VISIBLE
        retry?.visibility = if (canRetry) View.VISIBLE else View.GONE
    }

    private fun hideMessage() {
        messageBox?.visibility = View.GONE
    }

    private fun nextSafeSearch(current: SafeSearch): SafeSearch = when (current) {
        SafeSearch.STRICT -> SafeSearch.MODERATE
        SafeSearch.MODERATE -> SafeSearch.OFF
        SafeSearch.OFF -> SafeSearch.STRICT
    }

    private fun showActions(result: SearchResult) {
        val context = requireContext()
        val labels = arrayOf(
            getString(R.string.search_action_open),
            getString(R.string.search_action_copy),
            getString(R.string.search_action_share)
        )
        AlertDialog.Builder(context)
            .setTitle(result.host)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> open(result)
                    1 -> {
                        if (SearchLinkActions.copy(context, result.url)) {
                            Toast.makeText(context, R.string.search_copied, Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> SearchLinkActions.share(context, result.url, getString(R.string.search_share_title))
                }
            }
            .setNegativeButton(R.string.search_cancel, null)
            .show()
    }

    private fun open(result: SearchResult) {
        if (result.secure) {
            launchBrowser(result.url)
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.search_open_http_title)
            .setMessage(R.string.search_open_http_message)
            .setPositiveButton(R.string.search_open_anyway) { _, _ -> launchBrowser(result.url) }
            .setNegativeButton(R.string.search_cancel, null)
            .show()
    }

    private fun launchBrowser(url: String) {
        if (!SearchLinkActions.openInBrowser(requireContext(), url)) {
            Toast.makeText(requireContext(), R.string.search_no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        input = null
        summary = null
        showHttp = null
        progress = null
        messageBox = null
        message = null
        retry = null
        chipHttps = null
        chipSafe = null
        super.onDestroyView()
    }

    private companion object {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val http = OkHttpPageFetcher.duckDuckGo()
                val fetcher = RecordingPageFetcher(http)
                return SearchViewModel(DuckDuckGoSearch(fetcher), onRelease = { http.close() }) as T
            }
        }
    }
}
