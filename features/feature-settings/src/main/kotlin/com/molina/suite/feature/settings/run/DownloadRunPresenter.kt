package com.molina.suite.feature.settings.run

import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import com.molina.suite.feature.settings.R

/** Isi layar progres Quick Downloader: kartu judul, hasil, kecepatan, ukuran, sisa waktu, dan mesin. */
internal object DownloadRunPresenter : RunPresenter {

    override val layout: Int = R.layout.run_content_download

    override val doneMessage: Int = R.string.run_download_done

    override fun onInflated(root: View) {
        label(root, R.id.dl_speed, R.string.run_info_speed)
        label(root, R.id.dl_size, R.string.run_info_size)
        label(root, R.id.dl_eta, R.string.run_info_eta)
        label(root, R.id.dl_engine, R.string.run_info_engine)
    }

    override fun stageLabel(stage: String): Int? = when (stage) {
        "install" -> R.string.run_stage_install_tools
        "download" -> R.string.run_stage_dl_media
        "process" -> R.string.run_stage_process_media
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
        val info = DownloadInfo.from(status, ended)
        val ctx = root.context

        text(root, R.id.dl_title_name, info.title ?: "—")
        optionalText(root, R.id.dl_title_detail, info.titleDetail)
        text(root, R.id.dl_out_name, info.output ?: "—")
        optionalText(root, R.id.dl_out_detail, info.outputDetail)

        val working = R.string.run_info_working
        val unavailable = R.string.run_info_unavailable
        val finishedOk = snapshot is RunSnapshot.Finished && snapshot.status.state == RunState.DONE
        InfoTileBinder.bind(root.findViewById(R.id.dl_speed), info.speed, unavailable, working)
        InfoTileBinder.bind(root.findViewById(R.id.dl_size), info.size, unavailable, working)
        InfoTileBinder.bind(
            root.findViewById(R.id.dl_eta),
            info.eta,
            if (finishedOk) R.string.run_info_eta_done else unavailable,
            working
        )

        val engineTile = if (info.engine == null) {
            if (ended) InfoTile.Failed else InfoTile.Pending
        } else {
            InfoTile.Value(
                ctx.getString(engineLabel(info.engine)),
                info.stream?.let { ctx.getString(R.string.run_info_stream, it.index, it.count) }
            )
        }
        InfoTileBinder.bind(root.findViewById(R.id.dl_engine), engineTile, unavailable, working)
    }

    @StringRes
    private fun engineLabel(key: String): Int =
        if (key == "aria2") R.string.tools_engine_aria2 else R.string.tools_engine_ytdlp

    private fun label(root: View, tileId: Int, @StringRes text: Int) {
        InfoTileBinder.label(root.findViewById(tileId), text)
    }

    private fun text(root: View, id: Int, value: String) {
        root.findViewById<TextView>(id).text = value
    }

    private fun optionalText(root: View, id: Int, value: String?) {
        val view = root.findViewById<TextView>(id)
        if (value == null) {
            view.visibility = View.GONE
        } else {
            view.visibility = View.VISIBLE
            view.text = value
        }
    }
}
