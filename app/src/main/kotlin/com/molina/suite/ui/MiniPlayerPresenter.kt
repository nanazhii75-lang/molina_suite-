package com.molina.suite.ui

import android.app.Activity
import android.view.View
import com.molina.suite.R
import com.molina.suite.core.common.PlaybackBoard
import com.molina.suite.core.common.PlaybackSnapshot
import com.molina.suite.databinding.ActivityMainBinding
import java.util.Locale

/**
 * Menampilkan mini-player di atas navigasi bawah. Hanya membaca [PlaybackSnapshot] dan memanggil
 * [PlaybackBoard.controls]; tidak mengenal kelas mpv.
 */
class MiniPlayerPresenter(
    private val activity: Activity,
    private val board: PlaybackBoard,
    private val views: ActivityMainBinding
) {
    private var latest: PlaybackSnapshot? = null
    private var suppressed = false

    init {
        views.miniInfo.setOnClickListener { board.controls?.openPlayer(activity) }
        views.miniPlayPause.setOnClickListener { board.controls?.togglePause() }
        views.miniPrev.setOnClickListener { board.controls?.previous() }
        views.miniNext.setOnClickListener { board.controls?.next() }
        views.miniClose.setOnClickListener { board.controls?.close() }
    }

    fun render(snapshot: PlaybackSnapshot?) {
        latest = snapshot
        apply()
    }

    /** Mode ringkas shell menyembunyikan mini-player bersama header dan navigasi. */
    fun setSuppressed(value: Boolean) {
        suppressed = value
        apply()
    }

    private fun apply() {
        val snapshot = latest
        if (snapshot == null || suppressed) {
            views.miniPlayer.visibility = View.GONE
            return
        }
        views.miniPlayer.visibility = View.VISIBLE
        views.miniTitle.text = snapshot.title.ifBlank { activity.getString(R.string.molina_mini_untitled) }
        views.miniTime.text = if (snapshot.durationSec > 0) {
            activity.getString(R.string.molina_mini_time, clock(snapshot.positionSec), clock(snapshot.durationSec))
        } else {
            clock(snapshot.positionSec)
        }
        val max = snapshot.durationSec.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        views.miniProgress.max = max
        views.miniProgress.progress = snapshot.positionSec.coerceIn(0L, max.toLong()).toInt()
        views.miniPlayPause.setImageResource(
            if (snapshot.paused) R.drawable.ic_mini_play else R.drawable.ic_mini_pause
        )
        views.miniPlayPause.contentDescription = activity.getString(
            if (snapshot.paused) R.string.molina_mini_play else R.string.molina_mini_pause
        )
        val listVisibility = if (snapshot.hasPlaylist) View.VISIBLE else View.GONE
        views.miniPrev.visibility = listVisibility
        views.miniNext.visibility = listVisibility
    }

    private fun clock(totalSec: Long): String {
        val s = if (totalSec < 0) 0L else totalSec
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%d:%02d", m, sec)
    }
}
