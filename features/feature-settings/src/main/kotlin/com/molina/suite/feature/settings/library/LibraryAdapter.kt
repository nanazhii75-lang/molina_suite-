package com.molina.suite.feature.settings.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.molina.suite.feature.settings.R

internal class LibraryAdapter(
    private val onRun: (LibraryEntry) -> Unit,
    private val onEdit: (LibraryEntry) -> Unit
) : ListAdapter<LibraryEntry, LibraryAdapter.Holder>(LibraryDiff) {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.library_item_name)
        val content: TextView = view.findViewById(R.id.library_item_content)
        val note: TextView = view.findViewById(R.id.library_item_note)
        val run: ImageButton = view.findViewById(R.id.library_item_run)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_library_entry, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = getItem(position)
        holder.name.text = entry.name
        holder.content.text = entry.content
        holder.note.text = entry.note
        holder.note.visibility = if (entry.note.isEmpty()) View.GONE else View.VISIBLE
        holder.itemView.setOnClickListener { onEdit(entry) }
        holder.run.setOnClickListener { onRun(entry) }
    }
}

private object LibraryDiff : DiffUtil.ItemCallback<LibraryEntry>() {
    override fun areItemsTheSame(oldItem: LibraryEntry, newItem: LibraryEntry): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: LibraryEntry, newItem: LibraryEntry): Boolean =
        oldItem == newItem
}
