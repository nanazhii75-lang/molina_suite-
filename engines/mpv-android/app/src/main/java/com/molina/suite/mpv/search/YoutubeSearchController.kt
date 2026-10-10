package com.molina.suite.mpv.search

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Pemilik keadaan pencarian YouTube. Dipanggil hanya dari thread utama; pencarian berjalan di
 * thread latar dan hasilnya diposting kembali ke thread utama. Keadaan terakhir disimpan di
 * proses ini, sehingga kembali dari pemutar atau membuat ulang tampilan tidak mencari ulang.
 */
object YoutubeSearchController {

    const val RESULT_LIMIT = 10
    private const val TAG = "YoutubeSearchCtl"

    private val main = Handler(Looper.getMainLooper())
    private val observers = ArrayList<(SearchState) -> Unit>()
    private var generation = 0
    private var worker: Thread? = null

    var state: SearchState = SearchState.Idle
        private set

    /** Mendaftarkan pengamat dan langsung memanggilnya dengan keadaan saat ini. */
    fun observe(observer: (SearchState) -> Unit) {
        observers.add(observer)
        observer(state)
    }

    fun removeObserver(observer: (SearchState) -> Unit) {
        observers.remove(observer)
    }

    /** Memulai pencarian baru dan membatalkan yang sedang berjalan. */
    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        worker?.interrupt()
        generation += 1
        val gen = generation
        publish(SearchState.Loading(trimmed, 0, RESULT_LIMIT))

        val source = try {
            YoutubeSearch.current()
        } catch (e: YoutubeSearchException) {
            publish(SearchState.Failed(trimmed, e.reason, e.message ?: ""))
            return
        }
        worker = Thread({ runSearch(source, trimmed, gen) }, "ytdl-search").apply {
            isDaemon = true
            start()
        }
    }

    private fun runSearch(source: YoutubeSearchSource, query: String, gen: Int) {
        val outcome: SearchState = try {
            val items = source.search(query, RESULT_LIMIT) { done ->
                post(gen, SearchState.Loading(query, done.coerceAtMost(RESULT_LIMIT), RESULT_LIMIT))
            }
            SearchState.Results(query, items)
        } catch (e: YoutubeSearchException) {
            SearchState.Failed(query, e.reason, e.message ?: "")
        } catch (e: RuntimeException) {
            Log.e(TAG, "Pencarian gagal tak terduga", e)
            SearchState.Failed(query, YoutubeSearchException.Reason.FAILED, e.message ?: "")
        }
        post(gen, outcome)
    }

    private fun post(gen: Int, next: SearchState) {
        main.post {
            if (gen != generation) return@post // pencarian sudah digantikan
            // progres yang terlambat tidak boleh menimpa hasil akhir
            if (next is SearchState.Loading && state !is SearchState.Loading) return@post
            publish(next)
        }
    }

    private fun publish(next: SearchState) {
        state = next
        for (observer in observers.toList()) observer(next)
    }
}
