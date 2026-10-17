package com.molina.suite.feature.mpv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.util.Log
import com.molina.suite.core.common.MediaOpenResult
import com.molina.suite.core.common.MediaOpener
import java.io.File

/** Pembuka media untuk modul lain: memeriksa berkas lalu membuka pemutar mpv dengan jalur yang sama seperti molina-play. */
internal class MpvMediaOpener : MediaOpener {

    override fun canOpen(file: File): Boolean = MediaFileNames.isPlayable(file.name)

    override fun open(host: Activity, file: File): MediaOpenResult {
        val target = CommandInput.playTarget(file.absolutePath)
            ?: return MediaOpenResult.Rejected(host.getString(R.string.molina_media_unreadable))
        return try {
            host.startActivity(MpvPlayIntent.create(host, target))
            MediaOpenResult.Started
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "pemutar tidak bisa dibuka", e)
            MediaOpenResult.Rejected(host.getString(R.string.molina_media_no_player))
        }
    }

    private companion object {
        const val TAG = "MpvMediaOpener"
    }
}
