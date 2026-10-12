package com.molina.suite.mpv.search

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.molina.suite.mpv.R

/** Adapter daftar hasil pencarian; klik item diteruskan ke [onPlay]. */
internal class YoutubeResultAdapter(
    private val onPlay: (YoutubeResult) -> Unit
) : RecyclerView.Adapter<YoutubeResultAdapter.Holder>() {

    private var items: List<YoutubeResult> = emptyList()

    fun submit(newItems: List<YoutubeResult>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_youtube_result, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], position + 1)
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val thumb: ImageView = view.findViewById(R.id.thumb)
        private val indexBadge: TextView = view.findViewById(R.id.indexBadge)
        private val durationBadge: TextView = view.findViewById(R.id.durationBadge)
        private val title: TextView = view.findViewById(R.id.title)
        private val channel: TextView = view.findViewById(R.id.channel)
        private val stats: TextView = view.findViewById(R.id.stats)

        fun bind(item: YoutubeResult, number: Int) {
            val context = itemView.context
            title.text = item.title
            channel.text = item.channel
            indexBadge.text = number.toString()
            val duration = item.durationSeconds
            if (duration == null) {
                durationBadge.text = context.getString(R.string.molina_live)
                durationBadge.setBackgroundResource(R.drawable.molina_badge_live)
            } else {
                durationBadge.text = ResultFormat.duration(duration)
                durationBadge.setBackgroundResource(R.drawable.molina_badge_dark)
            }
            stats.text = buildStats(context, item)
            ThumbnailLoader.load(thumb, item.thumbnailUrl)
            itemView.setOnClickListener { onPlay(item) }
        }

        private fun buildStats(context: Context, item: YoutubeResult): CharSequence {
            val builder = SpannableStringBuilder()
            item.viewCount?.let { builder.append(ResultFormat.views(context, it)) }
            item.publishedAtMillis?.let { millis ->
                if (builder.isNotEmpty()) builder.append(" · ")
                val start = builder.length
                builder.append(ResultFormat.age(context, millis))
                builder.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(context, R.color.molina_folder)),
                    start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return builder
        }
    }
}
