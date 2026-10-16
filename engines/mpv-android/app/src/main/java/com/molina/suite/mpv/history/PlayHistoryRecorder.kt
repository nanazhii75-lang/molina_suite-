package com.molina.suite.mpv.history

import android.util.Log
import com.molina.suite.mpv.MPVLib

/** Membaca pemutaran yang sedang berjalan dari mpv menjadi satu entri riwayat. */
internal object PlayHistoryRecorder {

    private const val TAG = "PlayHistoryRecorder"
    private const val NEAR_END_SECONDS = 3.0

    /**
     * Entri untuk pemutaran saat ini, atau null bila tidak bisa diputar ulang (mis. fd:// dari
     * content URI) atau mpv tidak sedang memuat apa pun. Dipanggil sebelum mpv dihentikan.
     */
    fun capture(titleHint: String?): PlayHistoryEntry? {
        return try {
            val path = MPVLib.getPropertyString("path")
            if (path.isNullOrEmpty() || !isReplayable(path)) return null

            val mediaTitle = MPVLib.getPropertyString("media-title")
            val title = (listOf(mediaTitle, titleHint)
                .firstOrNull { !it.isNullOrBlank() && it != path }
                ?: path.substringAfterLast('/')).trim()

            val position = (MPVLib.getPropertyDouble("time-pos") ?: 0.0).coerceAtLeast(0.0)
            val duration = (MPVLib.getPropertyDouble("duration") ?: 0.0).coerceAtLeast(0.0)
            val eof = MPVLib.getPropertyBoolean("eof-reached") ?: false
            val finished = eof || (duration > 0.0 && duration - position <= NEAR_END_SECONDS)

            PlayHistoryEntry(
                path = path,
                title = title,
                positionSeconds = if (finished) 0L else position.toLong(),
                durationSeconds = duration.toLong(),
                finished = finished,
                updatedAtMillis = System.currentTimeMillis()
            )
        } catch (e: RuntimeException) {
            Log.w(TAG, "Gagal membaca pemutaran untuk riwayat", e)
            null
        }
    }

    private fun isReplayable(path: String): Boolean =
        path.startsWith("/") || path.startsWith("http://") || path.startsWith("https://")
}
