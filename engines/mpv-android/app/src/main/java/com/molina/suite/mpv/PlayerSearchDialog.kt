package com.molina.suite.mpv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.molina.suite.mpv.databinding.DialogPlayerSearchBinding
import com.molina.suite.mpv.search.YoutubeResult
import com.molina.suite.mpv.search.YoutubeResultAdapter
import com.molina.suite.mpv.search.YoutubeSearch
import com.molina.suite.mpv.search.YoutubeSearchController
import com.molina.suite.mpv.search.YoutubeSearchException
import com.molina.suite.mpv.search.YoutubeSearchSource

/**
 * Isi panel pencarian di dalam pemutar. Memakai penyedia pencarian yang sama dengan home mpv,
 * tetapi keadaannya sendiri: pencarian di sini tidak mengubah hasil, halaman, atau antrean di
 * home. Memilih hasil memanggil [onPick]; pemutar yang menyisipkan dan memutarnya.
 */
internal class PlayerSearchDialog(private val onPick: (YoutubeResult) -> Unit) {

    private lateinit var binding: DialogPlayerSearchBinding
    private val main = Handler(Looper.getMainLooper())
    private val adapter = YoutubeResultAdapter(onPlay = { onPick(it) })
    private var generation = 0
    private var worker: Thread? = null

    fun buildView(inflater: LayoutInflater): View {
        binding = DialogPlayerSearchBinding.inflate(inflater)
        binding.psList.layoutManager = LinearLayoutManager(binding.root.context)
        binding.psList.adapter = adapter
        binding.psGo.setOnClickListener { submit() }
        binding.psQuery.setOnEditorActionListener { _, actionId, event ->
            val enter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                submit()
                true
            } else {
                false
            }
        }
        showMessage(binding.root.context.getString(R.string.molina_player_search_idle), progress = false)
        binding.psQuery.requestFocus()
        return binding.root
    }

    /** Menghentikan pencarian yang berjalan; hasil yang terlambat diabaikan. */
    fun release() {
        generation += 1
        worker?.interrupt()
        worker = null
        binding.psList.adapter = null
    }

    private fun submit() {
        val query = binding.psQuery.text.toString().trim()
        if (query.isEmpty()) return
        hideKeyboard()
        val source = try {
            YoutubeSearch.current()
        } catch (e: YoutubeSearchException) {
            showError(e)
            return
        }
        worker?.interrupt()
        generation += 1
        val gen = generation
        adapter.submit(emptyList())
        showLoading(0)
        worker = Thread({ runSearch(source, query, gen) }, "player-search").apply {
            isDaemon = true
            start()
        }
    }

    private fun runSearch(source: YoutubeSearchSource, query: String, gen: Int) {
        try {
            val found = source.search(query, 0, RESULT_LIMIT) { done ->
                post(gen) { showLoading(done) }
            }
            post(gen) { showResults(query, found) }
        } catch (e: YoutubeSearchException) {
            post(gen) { showError(e) }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Pencarian di pemutar gagal tak terduga", e)
            post(gen) {
                showMessage(
                    binding.root.context.getString(R.string.molina_results_error_failed, e.message ?: "-"),
                    progress = false
                )
            }
        }
    }

    private fun post(gen: Int, action: () -> Unit) {
        main.post {
            if (gen == generation) action()
        }
    }

    private fun showLoading(done: Int) {
        val context = binding.root.context
        val text = if (done == 0) context.getString(R.string.molina_results_loading_start)
        else context.getString(R.string.molina_results_loading, done, YoutubeSearchController.PAGE_SIZE)
        showMessage(text, progress = true)
    }

    private fun showResults(query: String, items: List<YoutubeResult>) {
        if (items.isEmpty()) {
            showMessage(binding.root.context.getString(R.string.molina_results_none, query), progress = false)
            return
        }
        adapter.submit(items)
        binding.psList.scrollToPosition(0)
        binding.psMessage.visibility = View.GONE
        binding.psList.visibility = View.VISIBLE
    }

    private fun showError(error: YoutubeSearchException) {
        val context = binding.root.context
        val text = when (error.reason) {
            YoutubeSearchException.Reason.UNAVAILABLE -> context.getString(R.string.molina_results_error_unavailable)
            YoutubeSearchException.Reason.TIMEOUT -> context.getString(R.string.molina_results_error_timeout)
            YoutubeSearchException.Reason.FAILED ->
                context.getString(R.string.molina_results_error_failed, error.message ?: "-")
        }
        showMessage(text, progress = false)
    }

    private fun showMessage(text: String, progress: Boolean) {
        binding.psList.visibility = View.GONE
        binding.psMessage.visibility = View.VISIBLE
        binding.psProgress.visibility = if (progress) View.VISIBLE else View.GONE
        binding.psStatus.text = text
    }

    private fun hideKeyboard() {
        val manager = binding.root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        manager.hideSoftInputFromWindow(binding.psQuery.windowToken, 0)
    }

    private companion object {
        const val TAG = "PlayerSearch"
        /** Dua halaman hasil home sekaligus; sama dengan batas satu panggilan penyedia pencarian. */
        const val RESULT_LIMIT = YoutubeSearchController.PAGE_SIZE * 2
    }
}
