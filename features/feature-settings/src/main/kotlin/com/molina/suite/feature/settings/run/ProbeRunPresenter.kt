package com.molina.suite.feature.settings.run

import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
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
        val reading = R.string.run_info_reading
        InfoTileBinder.bind(root.findViewById(R.id.info_duration), info.duration, R.string.run_info_unknown, reading)
        InfoTileBinder.bind(root.findViewById(R.id.info_resolution), info.resolution, R.string.run_info_no_video, reading)
        InfoTileBinder.bind(root.findViewById(R.id.info_video), info.video, R.string.run_info_no_video, reading)
        InfoTileBinder.bind(root.findViewById(R.id.info_audio), info.audio, R.string.run_info_no_audio, reading)
    }

    private fun label(root: View, tileId: Int, @StringRes text: Int) {
        InfoTileBinder.label(root.findViewById(tileId), text)
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
}
