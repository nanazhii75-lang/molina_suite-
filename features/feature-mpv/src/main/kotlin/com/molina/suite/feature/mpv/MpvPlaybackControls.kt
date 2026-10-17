package com.molina.suite.feature.mpv

import android.app.Activity
import android.util.Log
import com.molina.suite.core.common.PlaybackControls
import com.molina.suite.mpv.MPVLib
import com.molina.suite.mpv.MpvSessionControl

/** Kendali mini-player ke mpv. Dipanggil dari UI shell, hanya saat ada pemutaran. */
internal class MpvPlaybackControls : PlaybackControls {

    override fun togglePause() = command("cycle", "pause")

    override fun previous() = command("playlist-prev")

    override fun next() = command("playlist-next")

    override fun openPlayer(host: Activity) {
        MpvSessionControl.bringToFront(host)
    }

    override fun close() {
        MpvSessionControl.requestClose()
    }

    private fun command(vararg args: String) {
        try {
            MPVLib.command(arrayOf(*args))
        } catch (e: RuntimeException) {
            Log.w(TAG, "mini-player: perintah mpv gagal: ${args.firstOrNull()}", e)
        }
    }

    private companion object {
        const val TAG = "mpv"
    }
}
