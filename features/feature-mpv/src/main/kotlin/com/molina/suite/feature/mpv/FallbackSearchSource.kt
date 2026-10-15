package com.molina.suite.feature.mpv

import android.util.Log
import com.molina.suite.mpv.search.YoutubeResult
import com.molina.suite.mpv.search.YoutubeSearchException
import com.molina.suite.mpv.search.YoutubeSearchException.Reason
import com.molina.suite.mpv.search.YoutubeSearchSource

/**
 * Mencoba [primary]; bila gagal membaca YouTube (Reason.FAILED, bukan pembatalan), beralih ke
 * [secondary]. Timeout dan ketidaktersediaan tidak dialihkan karena cadangan tidak akan membantu.
 */
internal class FallbackSearchSource(
    private val primary: YoutubeSearchSource,
    private val secondary: YoutubeSearchSource
) : YoutubeSearchSource {

    override fun search(
        query: String,
        offset: Int,
        limit: Int,
        onProgress: (Int) -> Unit
    ): List<YoutubeResult> =
        try {
            primary.search(query, offset, limit, onProgress)
        } catch (e: YoutubeSearchException) {
            if (e.reason != Reason.FAILED ||
                e.cause is InterruptedException ||
                Thread.currentThread().isInterrupted
            ) {
                throw e
            }
            Log.w(TAG, "Sumber utama gagal, beralih ke cadangan: " + MpvLogText.redact(e.message.orEmpty()))
            secondary.search(query, offset, limit, onProgress)
        }

    private companion object {
        const val TAG = "FallbackSearch"
    }
}
