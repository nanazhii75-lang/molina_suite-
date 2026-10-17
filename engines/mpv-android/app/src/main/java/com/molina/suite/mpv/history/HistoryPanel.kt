package com.molina.suite.mpv.history

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.molina.suite.mpv.R
import com.molina.suite.mpv.Utils

/**
 * Bagian riwayat di home mpv. Hanya menampilkan [PlayHistory]; apa yang terjadi saat item diketuk
 * diserahkan ke [onOpen]; tekan lama diserahkan ke [onActions] (menu aksi item).
 */
internal class HistoryPanel(
    root: View,
    private val onActions: (PlayHistoryEntry) -> Unit,
    private val onOpen: (PlayHistoryEntry) -> Unit
) {
    private val context = root.context
    private val section: View = root.findViewById(R.id.historySection)
    private val list: LinearLayout = root.findViewById(R.id.historyList)
    private val clear: View = root.findViewById(R.id.historyClear)

    fun attach() {
        clear.setOnClickListener {
            PlayHistory.clear(context)
            refresh()
        }
    }

    fun detach() {
        clear.setOnClickListener(null)
        list.removeAllViews()
    }

    fun refresh() {
        val entries = PlayHistory.load(context).take(MAX_SHOWN)
        list.removeAllViews()
        section.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        val inflater = LayoutInflater.from(context)
        for (entry in entries) {
            val row = inflater.inflate(R.layout.item_molina_history, list, false)
            row.findViewById<TextView>(R.id.historyTitle).text = entry.title
            row.findViewById<TextView>(R.id.historyMeta).text = metaText(entry)
            val progress = row.findViewById<ProgressBar>(R.id.historyProgress)
            if (entry.finished || entry.durationSeconds <= 0L) {
                progress.visibility = View.GONE
            } else {
                progress.progress = (entry.positionSeconds * 100 / entry.durationSeconds)
                    .toInt().coerceIn(0, 100)
            }
            row.setOnClickListener { onOpen(entry) }
            row.setOnLongClickListener {
                onActions(entry)
                true
            }
            list.addView(row)
        }
    }

    private fun metaText(entry: PlayHistoryEntry): String = when {
        entry.finished -> context.getString(R.string.molina_history_finished)
        entry.durationSeconds > 0L -> context.getString(
            R.string.molina_history_progress,
            Utils.prettyTime(entry.positionSeconds.toInt()),
            Utils.prettyTime(entry.durationSeconds.toInt())
        )
        else -> Utils.prettyTime(entry.positionSeconds.toInt())
    }

    private companion object {
        const val MAX_SHOWN = 10
    }
}
