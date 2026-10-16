package com.molina.suite.mpv

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.molina.suite.mpv.databinding.DialogPlayerSearchBinding
import com.molina.suite.mpv.search.YoutubeResult
import com.molina.suite.mpv.search.YoutubeResultAdapter
import com.molina.suite.mpv.search.YoutubeSearch
import com.molina.suite.mpv.search.YoutubeSearchController
import com.molina.suite.mpv.search.YoutubeSearchException
import com.molina.suite.mpv.search.YoutubeSearchSource

/**
 * Isi panel pencarian di dalam pemutar, dengan dua tab: "Cari" (judul) dan "Terkait" (video
 * terkait dengan yang sedang diputar). Memakai penyedia pencarian yang sama dengan home mpv,
 * tetapi keadaannya sendiri: tidak mengubah hasil, halaman, atau antrean di home. Memilih hasil
 * memanggil [onPick]; pemutar yang menyisipkan dan memutarnya. [currentUrl] mengembalikan
 * alamat item yang sedang diputar (dibaca di thread utama saat tab Terkait dibuka).
 */
internal class PlayerSearchDialog(
    private val currentUrl: () -> String?,
    private val onPick: (YoutubeResult) -> Unit
) {

    private enum class Tab { SEARCH, RELATED }

    private sealed interface Panel {
        data class Message(val text: String, val progress: Boolean) : Panel
        data class Items(val items: List<YoutubeResult>) : Panel
    }

    private lateinit var binding: DialogPlayerSearchBinding
    private val main = Handler(Looper.getMainLooper())
    private val adapter = YoutubeResultAdapter(onPlay = { onPick(it) })
    private var released = false
    private var tab = Tab.SEARCH
    private var searchPanel: Panel = Panel.Message("", false)
    private var relatedPanel: Panel? = null
    private var searchGen = 0
    private var searchWorker: Thread? = null
    private var relatedGen = 0
    private var relatedWorker: Thread? = null
    private var relatedLoading = false

    fun buildView(inflater: LayoutInflater): View {
        binding = DialogPlayerSearchBinding.inflate(inflater)
        val context = binding.root.context
        binding.psList.layoutManager = LinearLayoutManager(context)
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
        binding.psTabSearch.setOnClickListener { selectTab(Tab.SEARCH) }
        binding.psTabRelated.setOnClickListener { selectTab(Tab.RELATED) }
        searchPanel = Panel.Message(context.getString(R.string.molina_player_search_idle), false)
        selectTab(Tab.SEARCH)
        binding.psQuery.requestFocus()
        return binding.root
    }

    /** Menghentikan pencarian yang berjalan; hasil yang terlambat diabaikan. */
    fun release() {
        released = true
        searchGen += 1
        relatedGen += 1
        searchWorker?.interrupt()
        relatedWorker?.interrupt()
        searchWorker = null
        relatedWorker = null
        binding.psList.adapter = null
    }

    private fun selectTab(next: Tab) {
        tab = next
        styleTabs()
        binding.psQueryRow.visibility = if (next == Tab.SEARCH) View.VISIBLE else View.GONE
        if (next == Tab.RELATED) {
            hideKeyboard()
            if (relatedPanel !is Panel.Items && !relatedLoading) loadRelated()
        }
        draw(if (next == Tab.SEARCH) searchPanel else relatedPanel ?: Panel.Message("", false))
    }

    private fun styleTabs() {
        val context = binding.root.context
        val active = ContextCompat.getColor(context, R.color.molina_text)
        val idle = ContextCompat.getColor(context, R.color.molina_text_muted)
        for ((button, target) in listOf(binding.psTabSearch to Tab.SEARCH, binding.psTabRelated to Tab.RELATED)) {
            val selected = target == tab
            button.setTextColor(if (selected) active else idle)
            button.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun setPanel(target: Tab, panel: Panel) {
        if (target == Tab.SEARCH) searchPanel = panel else relatedPanel = panel
        if (target == tab) draw(panel)
    }

    private fun draw(panel: Panel) {
        when (panel) {
            is Panel.Message -> showMessage(panel.text, panel.progress)
            is Panel.Items -> {
                adapter.submit(panel.items)
                binding.psList.scrollToPosition(0)
                binding.psMessage.visibility = View.GONE
                binding.psList.visibility = View.VISIBLE
            }
        }
    }

    private fun submit() {
        val query = binding.psQuery.text.toString().trim()
        if (query.isEmpty()) return
        hideKeyboard()
        val source = try {
            YoutubeSearch.current()
        } catch (e: YoutubeSearchException) {
            setPanel(Tab.SEARCH, errorPanel(e))
            return
        }
        searchWorker?.interrupt()
        searchGen += 1
        val gen = searchGen
        setPanel(Tab.SEARCH, loadingPanel(0))
        searchWorker = Thread({ runSearch(source, query, gen) }, "player-search").apply {
            isDaemon = true
            start()
        }
    }

    private fun runSearch(source: YoutubeSearchSource, query: String, gen: Int) {
        try {
            val found = source.search(query, 0, RESULT_LIMIT) { done ->
                postSearch(gen) { setPanel(Tab.SEARCH, loadingPanel(done)) }
            }
            postSearch(gen) { setPanel(Tab.SEARCH, resultsPanel(query, found)) }
        } catch (e: YoutubeSearchException) {
            postSearch(gen) { setPanel(Tab.SEARCH, errorPanel(e)) }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Pencarian di pemutar gagal tak terduga", e)
            postSearch(gen) { setPanel(Tab.SEARCH, unexpectedPanel(e)) }
        }
    }

    private fun loadRelated() {
        val url = currentUrl()?.trim().orEmpty()
        val source = try {
            YoutubeSearch.current()
        } catch (e: YoutubeSearchException) {
            setPanel(Tab.RELATED, errorPanel(e))
            return
        }
        relatedWorker?.interrupt()
        relatedGen += 1
        val gen = relatedGen
        relatedLoading = true
        setPanel(
            Tab.RELATED,
            Panel.Message(binding.root.context.getString(R.string.molina_player_related_loading), true)
        )
        relatedWorker = Thread({ runRelated(source, url, gen) }, "player-related").apply {
            isDaemon = true
            start()
        }
    }

    private fun runRelated(source: YoutubeSearchSource, url: String, gen: Int) {
        try {
            val found = source.related(url, RELATED_LIMIT)
            postRelated(gen) {
                relatedLoading = false
                setPanel(Tab.RELATED, relatedResultsPanel(found))
            }
        } catch (e: YoutubeSearchException) {
            postRelated(gen) {
                relatedLoading = false
                setPanel(Tab.RELATED, errorPanel(e))
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Video terkait gagal tak terduga", e)
            postRelated(gen) {
                relatedLoading = false
                setPanel(Tab.RELATED, unexpectedPanel(e))
            }
        }
    }

    private fun postSearch(gen: Int, action: () -> Unit) {
        main.post {
            if (!released && gen == searchGen) action()
        }
    }

    private fun postRelated(gen: Int, action: () -> Unit) {
        main.post {
            if (!released && gen == relatedGen) action()
        }
    }

    private fun loadingPanel(done: Int): Panel {
        val context = binding.root.context
        val text = if (done == 0) context.getString(R.string.molina_results_loading_start)
        else context.getString(R.string.molina_results_loading, done, YoutubeSearchController.PAGE_SIZE)
        return Panel.Message(text, true)
    }

    private fun resultsPanel(query: String, items: List<YoutubeResult>): Panel =
        if (items.isEmpty()) {
            Panel.Message(binding.root.context.getString(R.string.molina_results_none, query), false)
        } else {
            Panel.Items(items)
        }

    private fun relatedResultsPanel(items: List<YoutubeResult>): Panel =
        if (items.isEmpty()) {
            Panel.Message(binding.root.context.getString(R.string.molina_player_related_none), false)
        } else {
            Panel.Items(items)
        }

    private fun errorPanel(error: YoutubeSearchException): Panel {
        val context = binding.root.context
        val text = when (error.reason) {
            YoutubeSearchException.Reason.UNAVAILABLE -> context.getString(R.string.molina_results_error_unavailable)
            YoutubeSearchException.Reason.TIMEOUT -> context.getString(R.string.molina_results_error_timeout)
            YoutubeSearchException.Reason.FAILED ->
                context.getString(R.string.molina_results_error_failed, error.message ?: "-")
        }
        return Panel.Message(text, false)
    }

    private fun unexpectedPanel(error: RuntimeException): Panel =
        Panel.Message(
            binding.root.context.getString(R.string.molina_results_error_failed, error.message ?: "-"),
            false
        )

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
        const val RELATED_LIMIT = 20
    }
}
