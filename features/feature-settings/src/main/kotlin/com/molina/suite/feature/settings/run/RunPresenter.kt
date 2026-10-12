package com.molina.suite.feature.settings.run

import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.molina.suite.feature.settings.R
import java.util.Locale

/** Mengisi bagian isi layar progres (kartu metrik) untuk satu jenis proses. */
internal interface RunPresenter {

    @get:LayoutRes
    val layout: Int

    /** Teks keberhasilan di bawah progress bar. */
    @get:StringRes
    val doneMessage: Int

    /** Isi layar dibuat sekali setelah [layout] di-inflate. */
    fun onInflated(root: View)

    /** Teks tahap yang sedang berjalan, atau null bila tidak ada. */
    @StringRes
    fun stageLabel(stage: String): Int?

    fun bind(root: View, snapshot: RunSnapshot)
}

internal object NetworkRunPresenter : RunPresenter {

    override val layout: Int = R.layout.run_content_network

    override val doneMessage: Int = R.string.run_message_done

    override fun onInflated(root: View) {
        label(root, R.id.net_ping, R.string.run_metric_ping)
        label(root, R.id.net_down, R.string.run_metric_download)
        label(root, R.id.net_up, R.string.run_metric_upload)
    }

    override fun stageLabel(stage: String): Int? = when (stage) {
        "install" -> R.string.run_stage_install
        "ping" -> R.string.run_stage_ping
        "download" -> R.string.run_stage_download
        "upload" -> R.string.run_stage_upload
        else -> null
    }

    override fun bind(root: View, snapshot: RunSnapshot) {
        val status = when (snapshot) {
            is RunSnapshot.Active -> snapshot.status
            is RunSnapshot.Finished -> snapshot.status
            is RunSnapshot.TimedOut -> snapshot.last
            is RunSnapshot.Waiting -> null
        }
        val ended = snapshot is RunSnapshot.Finished || snapshot is RunSnapshot.TimedOut
        val metrics = NetworkMetrics.from(status, ended)
        val ctx = root.context

        val pingDetail = metrics.pingDetail
        bindTile(root.findViewById(R.id.net_ping), metrics.ping, "ms", "%.0f")
        root.findViewById<View>(R.id.net_ping).findViewById<TextView>(R.id.metric_detail).apply {
            if (pingDetail == null) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = ctx.getString(
                    R.string.run_ping_detail,
                    pingDetail.min?.toString() ?: "-",
                    pingDetail.max?.toString() ?: "-",
                    pingDetail.jitter?.toString() ?: "-"
                )
            }
        }
        bindTile(root.findViewById(R.id.net_down), metrics.download, "Mbps", "%.1f")
        bindTile(root.findViewById(R.id.net_up), metrics.upload, "Mbps", "%.1f")

        val verdictView = root.findViewById<TextView>(R.id.net_verdict)
        val verdict = if (snapshot is RunSnapshot.Finished) metrics.verdict else null
        if (verdict == null) {
            verdictView.visibility = View.GONE
        } else {
            verdictView.visibility = View.VISIBLE
            verdictView.setText(
                when (verdict) {
                    Rating.GOOD -> R.string.run_verdict_good
                    Rating.FAIR -> R.string.run_verdict_fair
                    Rating.POOR -> R.string.run_verdict_poor
                }
            )
            verdictView.setTextColor(ContextCompat.getColor(ctx, ratingColor(verdict)))
        }
    }

    private fun label(root: View, tileId: Int, @StringRes text: Int) {
        root.findViewById<View>(tileId).findViewById<TextView>(R.id.metric_label).setText(text)
    }

    private fun bindTile(tile: View, state: MetricState, unit: String, format: String) {
        val ctx = tile.context
        val value = tile.findViewById<TextView>(R.id.metric_value)
        val unitView = tile.findViewById<TextView>(R.id.metric_unit)
        val dot = tile.findViewById<View>(R.id.metric_dot)

        val dotColor: Int
        when (state) {
            is MetricState.Pending -> {
                value.text = "—"
                value.setTextColor(ContextCompat.getColor(ctx, R.color.ms_text_secondary))
                unitView.setText(R.string.run_metric_pending)
                unitView.setTextColor(ContextCompat.getColor(ctx, R.color.ms_text_secondary))
                dotColor = R.color.ms_status_off
            }
            is MetricState.Measuring -> {
                value.text = "…"
                value.setTextColor(ContextCompat.getColor(ctx, R.color.ms_amber))
                unitView.setText(R.string.run_metric_measuring)
                unitView.setTextColor(ContextCompat.getColor(ctx, R.color.ms_amber))
                dotColor = R.color.ms_amber
            }
            is MetricState.Value -> {
                val color = ratingColor(state.rating)
                value.text = String.format(Locale.getDefault(), format, state.number)
                value.setTextColor(ContextCompat.getColor(ctx, color))
                unitView.text = unit
                unitView.setTextColor(ContextCompat.getColor(ctx, R.color.ms_text_secondary))
                dotColor = color
            }
            is MetricState.Failed -> {
                value.text = "—"
                value.setTextColor(ContextCompat.getColor(ctx, R.color.ms_status_error))
                unitView.setText(R.string.run_metric_failed)
                unitView.setTextColor(ContextCompat.getColor(ctx, R.color.ms_status_error))
                dotColor = R.color.ms_status_error
            }
        }
        dot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, dotColor))
    }

    @ColorRes
    private fun ratingColor(rating: Rating): Int = when (rating) {
        Rating.GOOD -> R.color.ms_status_on
        Rating.FAIR -> R.color.ms_amber
        Rating.POOR -> R.color.ms_status_error
    }
}
