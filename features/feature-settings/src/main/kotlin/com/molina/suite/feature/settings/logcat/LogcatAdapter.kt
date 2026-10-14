package com.molina.suite.feature.settings.logcat

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.molina.suite.feature.settings.R

/**
 * Daftar Logcat: judul aktivitas (ketuk untuk buka/tutup, tekan lama untuk menyalin seluruh
 * aktivitas) dan baris log berwarna menurut levelnya (tekan lama untuk menyalin barisnya).
 */
internal class LogcatAdapter(
    private val onLine: (LogcatEntry) -> Unit,
    private val onToggle: (LogRow.Header) -> Unit,
    private val onCopyGroup: (LogRow.Header) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private class LineHolder(val text: TextView) : RecyclerView.ViewHolder(text)

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.logcat_group_title)
        val meta: TextView = view.findViewById(R.id.logcat_group_meta)
    }

    private var rows: List<LogRow> = emptyList()

    fun submit(newRows: List<LogRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is LogRow.Header) TYPE_HEADER else TYPE_LINE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_logcat_group, parent, false))
        } else {
            LineHolder(inflater.inflate(R.layout.item_logcat_entry, parent, false) as TextView)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is LogRow.Header -> bindHeader(holder as HeaderHolder, row)
            is LogRow.Line -> bindLine(holder as LineHolder, row)
        }
    }

    private fun bindHeader(holder: HeaderHolder, row: LogRow.Header) {
        val context = holder.itemView.context
        val chevron = if (row.expanded) "\u25BE" else "\u25B8"
        holder.title.text = "$chevron ${row.category.label} \u00B7 ${row.title}"
        holder.title.setTextColor(context.getColor(colorOf(row.status)))
        holder.meta.text = metaOf(context, row)
        holder.itemView.setOnClickListener { onToggle(row) }
        holder.itemView.setOnLongClickListener {
            onCopyGroup(row)
            true
        }
    }

    private fun bindLine(holder: LineHolder, row: LogRow.Line) {
        val entry = row.entry
        val view = holder.text
        val repeats = if (entry.repeat > 1) "  \u00D7${entry.repeat}" else ""
        view.text = "${entry.time} ${entry.level.symbol}/${entry.tag}: ${entry.message}$repeats"
        view.setTextColor(view.context.getColor(colorOf(entry.level)))
        view.setTypeface(Typeface.MONOSPACE, if (entry.level >= LogcatLevel.ERROR) Typeface.BOLD else Typeface.NORMAL)
        val base = view.resources.getDimensionPixelSize(R.dimen.ms_space_md)
        val start = if (row.indented) base + (view.resources.displayMetrics.density * INDENT_DP).toInt() else base
        view.setPaddingRelative(start, view.paddingTop, base, view.paddingBottom)
        view.setOnLongClickListener {
            onLine(entry)
            true
        }
    }

    private fun metaOf(context: Context, row: LogRow.Header): String {
        val parts = ArrayList<String>(MAX_META_PARTS)
        parts.add(row.time)
        parts.add(
            context.getString(
                when (row.status) {
                    GroupStatus.OK -> R.string.logcat_group_status_ok
                    GroupStatus.WARN -> R.string.logcat_group_status_warn
                    GroupStatus.FAILED -> R.string.logcat_group_status_failed
                    GroupStatus.OPEN -> R.string.logcat_group_status_open
                }
            )
        )
        row.durationMillis?.let { parts.add(formatDuration(it)) }
        if (row.errors > 0) parts.add(context.getString(R.string.logcat_group_errors, row.errors))
        if (row.warns > 0) parts.add(context.getString(R.string.logcat_group_warns, row.warns))
        return parts.joinToString(" \u00B7 ")
    }

    private fun formatDuration(millis: Long): String = when {
        millis < MILLIS_PER_SECOND -> "$millis ms"
        millis < MILLIS_PER_MINUTE -> "${millis / MILLIS_PER_SECOND},${(millis % MILLIS_PER_SECOND) / 100} dtk"
        else -> "${millis / MILLIS_PER_MINUTE} mnt ${(millis % MILLIS_PER_MINUTE) / MILLIS_PER_SECOND} dtk"
    }

    private fun colorOf(status: GroupStatus): Int = when (status) {
        GroupStatus.OK -> R.color.ms_log_info
        GroupStatus.WARN -> R.color.ms_log_warn
        GroupStatus.FAILED -> R.color.ms_log_error
        GroupStatus.OPEN -> R.color.ms_log_verbose
    }

    private fun colorOf(level: LogcatLevel): Int = when (level) {
        LogcatLevel.VERBOSE -> R.color.ms_log_verbose
        LogcatLevel.DEBUG -> R.color.ms_log_debug
        LogcatLevel.INFO -> R.color.ms_log_info
        LogcatLevel.WARN -> R.color.ms_log_warn
        LogcatLevel.ERROR -> R.color.ms_log_error
        LogcatLevel.FATAL -> R.color.ms_log_fatal
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_LINE = 1
        const val INDENT_DP = 16
        const val MAX_META_PARTS = 5
        const val MILLIS_PER_SECOND = 1000L
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
