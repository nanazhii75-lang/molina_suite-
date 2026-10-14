package com.molina.suite.feature.settings.logcat

import android.view.LayoutInflater
import android.view.ViewGroup
import android.graphics.Typeface
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.molina.suite.feature.settings.R

/** Daftar baris logcat; tiap baris berwarna menurut levelnya, tekan lama untuk menyalin barisnya. */
internal class LogcatAdapter(
    private val onLongPress: (LogcatEntry) -> Unit
) : RecyclerView.Adapter<LogcatAdapter.Holder>() {

    internal class Holder(val text: TextView) : RecyclerView.ViewHolder(text)

    private var items: List<LogcatEntry> = emptyList()

    fun submit(entries: List<LogcatEntry>) {
        items = entries
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_logcat_entry, parent, false)
        return Holder(view as TextView)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = items[position]
        val view = holder.text
        val repeats = if (entry.repeat > 1) "  \u00D7${entry.repeat}" else ""
        view.text = "${entry.time} ${entry.level.symbol}/${entry.tag}: ${entry.message}$repeats"
        view.setTextColor(view.context.getColor(colorOf(entry.level)))
        view.setTypeface(Typeface.MONOSPACE, if (entry.level >= LogcatLevel.ERROR) Typeface.BOLD else Typeface.NORMAL)
        view.setOnLongClickListener {
            onLongPress(entry)
            true
        }
    }

    private fun colorOf(level: LogcatLevel): Int = when (level) {
        LogcatLevel.VERBOSE -> R.color.ms_log_verbose
        LogcatLevel.DEBUG -> R.color.ms_log_debug
        LogcatLevel.INFO -> R.color.ms_log_info
        LogcatLevel.WARN -> R.color.ms_log_warn
        LogcatLevel.ERROR -> R.color.ms_log_error
        LogcatLevel.FATAL -> R.color.ms_log_fatal
    }
}
