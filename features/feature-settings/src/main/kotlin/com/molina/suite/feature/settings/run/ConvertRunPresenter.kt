package com.molina.suite.feature.settings.run

import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import com.molina.suite.feature.settings.R

/** Isi layar progres Konverter Media: kartu sumber, hasil, kecepatan, ukuran, sisa waktu, dan diproses. */
internal object ConvertRunPresenter : RunPresenter {

    override val layout: Int = R.layout.run_content_convert

    override val doneMessage: Int = R.string.run_convert_done

    override fun onInflated(root: View) {
        label(root, R.id.conv_speed, R.string.run_info_speed)
        label(root, R.id.conv_size, R.string.run_info_size)
        label(root, R.id.conv_eta, R.string.run_info_eta)
        label(root, R.id.conv_time, R.string.run_info_processed)
    }

    override fun stageLabel(stage: String): Int? = when (stage) {
        "install" -> R.string.run_stage_install_ffmpeg
        "convert" -> R.string.run_stage_convert
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
        val info = ConvertInfo.from(status, ended)

        text(root, R.id.conv_src_name, info.source ?: "—")
        text(root, R.id.conv_out_name, info.output ?: "—")
        val detail = root.findViewById<TextView>(R.id.conv_out_detail)
        val detailText = info.outputDetail
        if (detailText == null) {
            detail.visibility = View.GONE
        } else {
            detail.visibility = View.VISIBLE
            detail.text = detailText
        }

        val working = R.string.run_info_working
        val unavailable = R.string.run_info_unavailable
        val finishedOk = snapshot is RunSnapshot.Finished && snapshot.status.state == RunState.DONE
        InfoTileBinder.bind(root.findViewById(R.id.conv_speed), info.speed, unavailable, working)
        InfoTileBinder.bind(root.findViewById(R.id.conv_size), info.size, unavailable, working)
        InfoTileBinder.bind(
            root.findViewById(R.id.conv_eta),
            info.eta,
            if (finishedOk) R.string.run_info_eta_done else unavailable,
            working
        )
        InfoTileBinder.bind(root.findViewById(R.id.conv_time), info.processed, unavailable, working)
    }

    private fun label(root: View, tileId: Int, @StringRes text: Int) {
        InfoTileBinder.label(root.findViewById(tileId), text)
    }

    private fun text(root: View, id: Int, value: String) {
        root.findViewById<TextView>(id).text = value
    }
}
