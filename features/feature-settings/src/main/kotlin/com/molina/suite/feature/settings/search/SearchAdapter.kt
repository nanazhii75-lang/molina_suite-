package com.molina.suite.feature.settings.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.molina.suite.feature.settings.R

/** Daftar hasil pencarian; ketukan diteruskan ke [onClick], tanpa logika bisnis di sini. */
class SearchAdapter(
    private val onClick: (SearchResult) -> Unit
) : ListAdapter<SearchResult, SearchAdapter.Holder>(Diff) {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.search_item_title)
        val host: TextView = view.findViewById(R.id.search_item_host)
        val badge: TextView = view.findViewById(R.id.search_item_badge)
        val snippet: TextView = view.findViewById(R.id.search_item_snippet)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        holder.title.text = item.title
        holder.host.text = item.host
        holder.badge.visibility = if (item.secure) View.GONE else View.VISIBLE
        holder.snippet.text = item.snippet
        holder.snippet.visibility = if (item.snippet.isEmpty()) View.GONE else View.VISIBLE
        holder.itemView.setOnClickListener { onClick(item) }
    }

    private object Diff : DiffUtil.ItemCallback<SearchResult>() {
        override fun areItemsTheSame(old: SearchResult, new: SearchResult) = old.url == new.url
        override fun areContentsTheSame(old: SearchResult, new: SearchResult) = old == new
    }
}
