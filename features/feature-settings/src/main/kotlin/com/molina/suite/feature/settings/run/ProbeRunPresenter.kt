package com.molina.suite.feature.settings.run

import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.molina.suite.feature.settings.R

/** Isi layar progres Info Video: kartu berkas, durasi, resolusi, video, dan audio. */
internal object ProbeRunPresenter : RunPresenter {

    override val layout: Int = R.layout.run_content_probe

    override val doneMessage: Int = R.string.run_probe_done

    override fun onInflated(root: View) {
        label(root, R.id.info_duration, R.string.run_info_duration)
        label(root, R.id.info_resolution, R.string.run_info_resolution)
        label(root, R.id.info_video, R.string.run_info_video)
        label(root, R.id.info_audio, R.string.run_info_audio)
    }

    override fun stageLabel(stage: String): Int? = when (stage) {
        "install" -> R.string.run_stage_install_ffmpeg
        "probe" -> R.string.run_stage_probe
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
        val info = ProbeInfo.from(status, ended)

        bindFile(root, info)
        bindTile(root.findViewById(R.id.info_duration), info.duration, R.string.run_info_unknown)
        bindTile(root.findViewById(R.id.info_resolution), info.resolution, R.string.run_info_no_video)
        bindTile(root.findViewById(R.id.info_video), info.video, R.string.run_info_no_video)
        bindTile(root.findViewById(R.id.info_audio), info.audio, R.string.run_info_no_audio)
    }

    private fun label(root: View, tileId: Int, @StringRes text: Int) {
        root.findViewById<View>(tileId).findViewById<TextView>(R.id.info_label).setText(text)
    }

    private fun bindFile(root: View, info: ProbeInfo) {
        val name = root.findViewById<TextView>(R.id.info_file_name)
        val detail = root.findViewById<TextView>(R.id.info_file_detail)
        name.text = info.fileName ?: "—"
        val text = info.fileDetail
        if (text == null) {
            detail.visibility = View.GONE
        } else {
            detail.visibility = View.VISIBLE
            detail.text = text
        }
    }

    private fun bindTile(tile: View, state: InfoTile, @StringRes absentText: Int) {
        val ctx = tile.context
        val value = tile.findViewById<TextView>(R.id.info_value)
        val detail = tile.findViewById<TextView>(R.id.info_detail)
        val dot = tile.findViewById<View>(R.id.info_dot)

        @ColorRes val valueColor: Int
        @ColorRes val dotColor: Int
        var detailText: String? = null
        when (state) {
            is InfoTile.Pending -> {
                value.text = "—"
                detailText = ctx.getString(R.string.run_metric_pending)
                valueColor = R.color.ms_text_secondary
                dotColor = R.color.ms_status_off
            }
            is InfoTile.Reading -> {
                value.text = "…"
                detailText = ctx.getString(R.string.run_info_reading)
                valueColor = R.color.ms_amber
                dotColor = R.color.ms_amber
            }
            is InfoTile.Absent -> {
                value.text = "—"
                detailText = ctx.getString(absentText)
                valueColor = R.color.ms_text_secondary
                dotColor = R.color.ms_status_off
            }
            is InfoTile.Failed -> {
                value.text = "—"
                detailText = ctx.getString(R.string.run_metric_failed)
                valueColor = R.color.ms_status_error
                dotColor = R.color.ms_status_error
            }
            is InfoTile.Value -> {
                value.text = state.main
                detailText = state.detail
                valueColor = R.color.ms_text_primary
                dotColor = R.color.ms_status_on
            }
        }
        value.setTextColor(ContextCompat.getColor(ctx, valueColor))
        dot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(ctx, dotColor))
        if (detailText == null) {
            detail.visibility = View.GONE
        } else {
            detail.visibility = View.VISIBLE
            detail.text = detailText
        }
    }
}
