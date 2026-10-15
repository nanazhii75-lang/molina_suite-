package com.molina.suite.mpv.search

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.res.ColorStateList
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.molina.suite.mpv.R

/** Adapter daftar hasil pencarian; klik item diteruskan ke [onPlay]. */
internal class YoutubeResultAdapter(
    private val onPlay: (YoutubeResult) -> Unit,
    private val isQueued: (String) -> Boolean,
    private val onToggleQueue: (YoutubeResult) -> Unit
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

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(QUEUE_PAYLOAD)) {
            holder.bindQueueState(items[position])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    /** Memperbarui hanya tombol antrean tiap item (tanpa memuat ulang thumbnail dan teks). */
    fun refreshQueueState() {
        if (items.isNotEmpty()) notifyItemRangeChanged(0, items.size, QUEUE_PAYLOAD)
    }

    private companion object {
        const val QUEUE_PAYLOAD = "queue"
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val thumb: ImageView = view.findViewById(R.id.thumb)
        private val indexBadge: TextView = view.findViewById(R.id.indexBadge)
        private val durationBadge: TextView = view.findViewById(R.id.durationBadge)
        private val title: TextView = view.findViewById(R.id.title)
        private val channel: TextView = view.findViewById(R.id.channel)
        private val stats: TextView = view.findViewById(R.id.stats)
        private val queueBtn: ImageButton = view.findViewById(R.id.queueBtn)

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
            queueBtn.setOnClickListener { onToggleQueue(item) }
            bindQueueState(item)
        }

        fun bindQueueState(item: YoutubeResult) {
            val context = itemView.context
            val queued = isQueued(item.id)
            queueBtn.setImageResource(
                if (queued) R.drawable.ic_molina_queue_check_24dp else R.drawable.ic_molina_queue_add_24dp
            )
            val tint = ContextCompat.getColor(
                context, if (queued) R.color.molina_folder else R.color.molina_youtube
            )
            ImageViewCompat.setImageTintList(queueBtn, ColorStateList.valueOf(tint))
            queueBtn.contentDescription = context.getString(
                if (queued) R.string.molina_queue_remove else R.string.molina_queue_add
            )
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
