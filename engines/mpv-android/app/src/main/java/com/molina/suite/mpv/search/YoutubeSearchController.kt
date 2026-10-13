package com.molina.suite.mpv.search

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Pemilik keadaan pencarian YouTube. Dipanggil hanya dari thread utama; pencarian berjalan di
 * thread latar dan hasilnya diposting kembali ke thread utama. Hasil dibagi per halaman
 * ([PAGE_SIZE] item); halaman yang sudah diambil disimpan sehingga kembali ke halaman
 * sebelumnya instan. Keadaan terakhir disimpan di proses ini, sehingga kembali dari pemutar
 * atau membuat ulang tampilan tidak mencari ulang.
 */
object YoutubeSearchController {

    const val PAGE_SIZE = 5
    const val MAX_PAGES = 6
    private const val TAG = "YoutubeSearchCtl"

    private class PageData(val items: List<YoutubeResult>, val hasNext: Boolean)

    private val main = Handler(Looper.getMainLooper())
    private val observers = ArrayList<(SearchState) -> Unit>()
    private val pages = HashMap<Int, PageData>()
    private var generation = 0
    private var worker: Thread? = null
    private var query = ""
    private var targetPage = 1

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

    /** Memulai pencarian baru dari halaman 1 dan membatalkan yang sedang berjalan. */
    fun search(newQuery: String) {
        val trimmed = newQuery.trim()
        if (trimmed.isEmpty()) return
        query = trimmed
        pages.clear()
        load(1)
    }

    /** Pindah ke halaman berikutnya bila ada. */
    fun nextPage() {
        val current = state as? SearchState.Results ?: return
        if (current.hasNext) load(current.page + 1)
    }

    /** Kembali ke halaman sebelumnya bila ada. */
    fun prevPage() {
        val current = state as? SearchState.Results ?: return
        if (current.page > 1) load(current.page - 1)
    }

    /** Semua hasil yang sudah diambil, urut halaman 1..n tanpa celah; kosong bila belum ada. */
    fun fetchedItems(): List<YoutubeResult> {
        val all = ArrayList<YoutubeResult>()
        var page = 1
        while (true) {
            val data = pages[page] ?: break
            all.addAll(data.items)
            page += 1
        }
        return all
    }

    /** Mengulang pengambilan halaman yang terakhir diminta (setelah gagal). */
    fun retry() {
        if (query.isNotEmpty()) load(targetPage)
    }

    /** Membatalkan pencarian yang berjalan dan mengembalikan panel ke keadaan awal. */
    fun reset() {
        worker?.interrupt()
        worker = null
        generation += 1 // hasil dari pencarian yang dibatalkan akan diabaikan
        pages.clear()
        query = ""
        targetPage = 1
        publish(SearchState.Idle)
    }

    private fun load(page: Int) {
        worker?.interrupt()
        generation += 1
        val gen = generation
        targetPage = page

        val cached = pages[page]
        if (cached != null) {
            publish(SearchState.Results(query, cached.items, page, cached.hasNext))
            return
        }

        publish(SearchState.Loading(query, 0, PAGE_SIZE, page))
        val source = try {
            YoutubeSearch.current()
        } catch (e: YoutubeSearchException) {
            publish(SearchState.Failed(query, e.reason, e.message ?: ""))
            return
        }
        val q = query
        worker = Thread({ runSearch(source, q, page, gen) }, "ytdl-search").apply {
            isDaemon = true
            start()
        }
    }

    private fun runSearch(source: YoutubeSearchSource, q: String, page: Int, gen: Int) {
        val offset = (page - 1) * PAGE_SIZE
        try {
            val fetched = source.search(q, offset, PAGE_SIZE) { done ->
                post(gen) {
                    // progres yang terlambat tidak boleh menimpa hasil akhir
                    if (state is SearchState.Loading) {
                        publish(SearchState.Loading(q, done.coerceAtMost(PAGE_SIZE), PAGE_SIZE, page))
                    }
                }
            }
            post(gen) { complete(q, page, fetched) }
        } catch (e: YoutubeSearchException) {
            post(gen) { publish(SearchState.Failed(q, e.reason, e.message ?: "")) }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Pencarian gagal tak terduga", e)
            post(gen) {
                publish(SearchState.Failed(q, YoutubeSearchException.Reason.FAILED, e.message ?: ""))
            }
        }
    }

    private fun complete(q: String, page: Int, fetched: List<YoutubeResult>) {
        // Urutan menurut waktu unggah bergeser bila ada video baru di antara dua pengambilan,
        // sehingga satu video bisa muncul di dua halaman: buang yang sudah tampil sebelumnya.
        val seen = HashSet<String>()
        for (p in 1 until page) {
            pages[p]?.items?.forEach { seen.add(it.id) }
        }
        val fresh = fetched.filter { it.id !in seen }
        val hasNext = fetched.size >= PAGE_SIZE && page < MAX_PAGES
        pages[page] = PageData(fresh, hasNext)
        publish(SearchState.Results(q, fresh, page, hasNext))
    }

    private fun post(gen: Int, action: () -> Unit) {
        main.post {
            if (gen != generation) return@post // pencarian sudah digantikan
            action()
        }
    }

    private fun publish(next: SearchState) {
        state = next
        for (observer in observers.toList()) observer(next)
    }
}
