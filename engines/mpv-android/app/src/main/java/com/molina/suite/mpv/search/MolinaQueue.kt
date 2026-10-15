package com.molina.suite.mpv.search

/**
 * Antrean putar yang disusun dari hasil pencarian. Tidak terikat satu kata kunci: video dari
 * beberapa pencarian dapat dikumpulkan lalu diputar sekaligus sebagai satu playlist. Urutan
 * sama dengan urutan penambahan. Hanya dipanggil dari thread utama; disimpan di memori proses.
 */
object MolinaQueue {

    /** Sama dengan batas playlist yang diterima pemutar. */
    const val MAX_ITEMS = MolinaPlaylist.MAX_ITEMS

    enum class Outcome { ADDED, REMOVED, FULL }

    private val items = ArrayList<YoutubeResult>()
    private val observers = ArrayList<() -> Unit>()

    val size: Int get() = items.size

    /** Salinan antrean saat ini, aman diubah pemanggil. */
    fun snapshot(): List<YoutubeResult> = ArrayList(items)

    fun contains(id: String): Boolean = items.any { it.id == id }

    /** Menambahkan [item] bila belum ada, mengeluarkannya bila sudah ada; FULL bila antrean penuh. */
    fun toggle(item: YoutubeResult): Outcome {
        val index = items.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            items.removeAt(index)
            publish()
            return Outcome.REMOVED
        }
        if (items.size >= MAX_ITEMS) return Outcome.FULL
        items.add(item)
        publish()
        return Outcome.ADDED
    }

    fun clear() {
        if (items.isEmpty()) return
        items.clear()
        publish()
    }

    /** Mendaftarkan pengamat dan langsung memanggilnya dengan keadaan saat ini. */
    fun observe(observer: () -> Unit) {
        observers.add(observer)
        observer()
    }

    fun removeObserver(observer: () -> Unit) {
        observers.remove(observer)
    }

    private fun publish() {
        for (observer in observers.toList()) observer()
    }
}
