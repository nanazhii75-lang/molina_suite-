package com.molina.suite.feature.settings.connections

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.molina.suite.feature.settings.R

/** Daftar host pada layar Catatan Koneksi. */
internal class ConnectionsAdapter : RecyclerView.Adapter<ConnectionsAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val host: TextView = view.findViewById(R.id.connection_host)
        val meta: TextView = view.findViewById(R.id.connection_meta)
    }

    private var rows: List<ConnectionRow> = emptyList()

    fun submit(newRows: List<ConnectionRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_connection, parent, false))

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        val context = holder.itemView.context
        val hostText = if (row.secure) R.string.connections_host_secure else R.string.connections_host_insecure
        holder.host.text = context.getString(hostText, row.hostLabel)
        holder.host.setTextColor(
            context.getColor(if (row.secure) R.color.ms_text_primary else R.color.ms_log_warn)
        )
        val ago = DateUtils.getRelativeTimeSpanString(
            row.lastAtMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
        )
        holder.meta.text = context.getString(R.string.connections_row_meta, row.source.label, row.count, ago)
    }
}
