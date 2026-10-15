package com.molina.suite.mpv.search

import android.view.View
import android.widget.TextView
import com.molina.suite.mpv.R

/**
 * Bar antrean di bawah daftar hasil: tampil hanya bila antrean tidak kosong, menunjukkan
 * jumlahnya, dan menyediakan tombol Putar dan Kosongkan. Hanya menampilkan [MolinaQueue];
 * memutar diserahkan ke [onPlay].
 */
internal class QueueBar(
    private val root: View,
    private val onPlay: (List<YoutubeResult>) -> Unit
) {
    private val count: TextView = root.findViewById(R.id.queueCount)
    private val play: View = root.findViewById(R.id.queuePlay)
    private val clear: View = root.findViewById(R.id.queueClear)
    private val observer: () -> Unit = { render() }

    fun attach() {
        play.setOnClickListener {
            val items = MolinaQueue.snapshot()
            if (items.isNotEmpty()) onPlay(items)
        }
        clear.setOnClickListener { MolinaQueue.clear() }
        MolinaQueue.observe(observer)
    }

    fun detach() {
        MolinaQueue.removeObserver(observer)
        play.setOnClickListener(null)
        clear.setOnClickListener(null)
    }

    private fun render() {
        val size = MolinaQueue.size
        root.visibility = if (size == 0) View.GONE else View.VISIBLE
        count.text = root.context.getString(R.string.molina_queue_count, size)
    }
}
