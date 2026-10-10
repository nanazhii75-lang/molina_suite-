package com.molina.suite.mpv.search

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.molina.suite.mpv.R

/**
 * Panel hasil pencarian di home mpv: hanya menampilkan [SearchState] dari
 * [YoutubeSearchController]. [highlight] diberi latar kapsul aktif selama ada pencarian.
 */
internal class YoutubeResultsPanel(
    root: View,
    private val highlight: View,
    onPlay: (YoutubeResult) -> Unit
) {
    private val context: Context = root.context
    private val header: TextView = root.findViewById(R.id.resultsHeader)
    private val order: View = root.findViewById(R.id.resultsOrder)
    private val list: RecyclerView = root.findViewById(R.id.resultsList)
    private val emptyView: View = root.findViewById(R.id.resultsEmpty)
    private val loadingView: View = root.findViewById(R.id.resultsLoading)
    private val loadingText: TextView = root.findViewById(R.id.resultsLoadingText)
    private val errorView: View = root.findViewById(R.id.resultsError)
    private val errorText: TextView = root.findViewById(R.id.resultsErrorText)
    private val retry: View = root.findViewById(R.id.resultsRetry)

    private val adapter = YoutubeResultAdapter(onPlay)
    private val idleBackground: Drawable? = highlight.background
    private var lastQuery = ""
    private val observer: (SearchState) -> Unit = { render(it) }

    fun attach() {
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        retry.setOnClickListener { YoutubeSearchController.search(lastQuery) }
        YoutubeSearchController.observe(observer)
    }

    fun detach() {
        YoutubeSearchController.removeObserver(observer)
        list.adapter = null
        retry.setOnClickListener(null)
    }

    private fun render(state: SearchState) {
        if (state is SearchState.Idle) {
            highlight.background = idleBackground
        } else {
            highlight.setBackgroundResource(R.drawable.molina_cap_active)
        }

        when (state) {
            is SearchState.Idle -> {
                header.setText(R.string.molina_results_title_idle)
                order.visibility = View.GONE
                showOnly(emptyView)
            }
            is SearchState.Loading -> {
                lastQuery = state.query
                header.text = context.getString(R.string.molina_results_header, state.query)
                order.visibility = View.GONE
                loadingText.text =
                    if (state.done == 0) context.getString(R.string.molina_results_loading_start)
                    else context.getString(R.string.molina_results_loading, state.done, state.total)
                showOnly(loadingView)
            }
            is SearchState.Results -> {
                lastQuery = state.query
                header.text = context.getString(R.string.molina_results_header, state.query)
                if (state.items.isEmpty()) {
                    errorText.text = context.getString(R.string.molina_results_none, state.query)
                    retry.visibility = View.GONE
                    order.visibility = View.GONE
                    showOnly(errorView)
                } else {
                    adapter.submit(state.items)
                    list.scrollToPosition(0)
                    order.visibility = View.VISIBLE
                    showOnly(list)
                }
            }
            is SearchState.Failed -> {
                lastQuery = state.query
                header.text = context.getString(R.string.molina_results_header, state.query)
                errorText.text = errorMessage(state)
                retry.visibility = View.VISIBLE
                order.visibility = View.GONE
                showOnly(errorView)
            }
        }
    }

    private fun errorMessage(state: SearchState.Failed): String = when (state.reason) {
        YoutubeSearchException.Reason.UNAVAILABLE -> context.getString(R.string.molina_results_error_unavailable)
        YoutubeSearchException.Reason.TIMEOUT -> context.getString(R.string.molina_results_error_timeout)
        YoutubeSearchException.Reason.FAILED ->
            context.getString(R.string.molina_results_error_failed, state.detail.ifEmpty { "-" })
    }

    private fun showOnly(target: View) {
        for (view in arrayOf(list, emptyView, loadingView, errorView)) {
            view.visibility = if (view === target) View.VISIBLE else View.GONE
        }
    }
}
