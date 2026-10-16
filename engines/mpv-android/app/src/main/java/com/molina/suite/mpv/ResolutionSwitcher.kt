package com.molina.suite.mpv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * Pilihan resolusi untuk stream online (yt-dlp lewat ytdl_hook). Memilih resolusi mengganti
 * opsi ytdl-format lalu memuat ulang entri yang sedang diputar dan kembali ke posisi semula;
 * isi playlist tidak berubah. Berkas lokal tidak punya varian resolusi, jadi tombolnya tidak
 * ditampilkan untuk berkas lokal ([isApplicable]).
 */
internal class ResolutionSwitcher {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 0 = otomatis, selebihnya indeks ke [HEIGHTS] + 1. */
    private var selected = 0

    /** Format bawaan dari host, direkam sebelum diganti pertama kali. */
    private var defaultFormat: String? = null

    fun isApplicable(): Boolean {
        val path = MPVLib.getPropertyString("path") ?: return false
        if (!path.startsWith("http://") && !path.startsWith("https://")) return false
        return MPVLib.getPropertyString("options/ytdl") == "yes"
    }

    fun show(context: Context, onClose: () -> Unit) {
        val labels = ArrayList<String>(HEIGHTS.size + 1)
        labels.add(context.getString(R.string.molina_resolution_auto))
        for (h in HEIGHTS) labels.add(context.getString(R.string.molina_resolution_item, h))

        AlertDialog.Builder(context)
            .setTitle(R.string.molina_resolution_title)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { dialog, which ->
                dialog.dismiss()
                if (which != selected) apply(context, which)
            }
            .setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.cancel() }
            .setOnDismissListener { onClose() }
            .show()
    }

    private fun apply(context: Context, index: Int) {
        val path = MPVLib.getPropertyString("path")
        if (path.isNullOrEmpty()) {
            Toast.makeText(context, R.string.molina_resolution_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val position = MPVLib.getPropertyDouble("time-pos") ?: 0.0
        val playlistPos = MPVLib.getPropertyInt("playlist-pos") ?: -1

        if (defaultFormat == null) {
            val current = MPVLib.getPropertyString("options/ytdl-format")
            defaultFormat = if (current.isNullOrEmpty()) formatFor(MAX_AUTO_HEIGHT) else current
        }
        val format = if (index == 0) defaultFormat!! else formatFor(HEIGHTS[index - 1])

        try {
            MPVLib.setPropertyString("ytdl-format", format)
            selected = index
            resumeAt(position)
            if (playlistPos >= 0)
                MPVLib.command(arrayOf("playlist-play-index", playlistPos.toString()))
            else
                MPVLib.command(arrayOf("loadfile", path, "replace", "-1"))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Gagal mengganti resolusi", e)
            Toast.makeText(context, R.string.molina_resolution_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** Setelah entri dimuat ulang, kembali ke posisi sebelum resolusi diganti. */
    private fun resumeAt(position: Double) {
        if (position < MIN_RESUME_SECONDS) return
        val observer = object : MPVLib.EventObserver {
            @Volatile private var done = false

            override fun event(eventId: Int) {
                if (eventId != MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED || done) return
                done = true
                MPVLib.command(arrayOf("seek", position.toString(), "absolute"))
                // daftar observer sedang diiterasi di sini; lepas lewat main thread
                mainHandler.post { MPVLib.removeObserver(this) }
            }

            override fun eventProperty(property: String) {}
            override fun eventProperty(property: String, value: Long) {}
            override fun eventProperty(property: String, value: Boolean) {}
            override fun eventProperty(property: String, value: String) {}
            override fun eventProperty(property: String, value: Double) {}
        }
        MPVLib.addObserver(observer)
        // bila pemuatan gagal, jangan biarkan pengamat tertinggal
        mainHandler.postDelayed({ MPVLib.removeObserver(observer) }, RESUME_TIMEOUT_MS)
    }

    private fun formatFor(height: Int): String =
        "bestvideo[height<=?$height][vcodec!*=?av01]+bestaudio/" +
            "best[height<=?$height][vcodec!*=?av01]/best"

    private companion object {
        const val TAG = "ResolutionSwitcher"
        val HEIGHTS = intArrayOf(1080, 720, 480)
        const val MAX_AUTO_HEIGHT = 1080
        const val MIN_RESUME_SECONDS = 1.0
        const val RESUME_TIMEOUT_MS = 60_000L
    }
}
