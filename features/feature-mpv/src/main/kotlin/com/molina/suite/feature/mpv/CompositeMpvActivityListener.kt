package com.molina.suite.feature.mpv

import android.util.Log
import com.molina.suite.mpv.MpvActivityListener
import com.molina.suite.mpv.MpvOpenKind

/** Meneruskan kejadian pemutar ke beberapa pendengar; kegagalan satu pendengar tidak menghentikan yang lain. */
internal class CompositeMpvActivityListener(
    private vararg val parts: MpvActivityListener
) : MpvActivityListener {

    override fun opened(session: Long, kind: MpvOpenKind) = each { it.opened(session, kind) }

    override fun note(session: Long, text: String, error: Boolean) = each { it.note(session, text, error) }

    override fun closed(session: Long, played: Boolean, failure: String?) =
        each { it.closed(session, played, failure) }

    override fun reached(session: Long, origin: String) = each { it.reached(session, origin) }

    private inline fun each(call: (MpvActivityListener) -> Unit) {
        for (part in parts) {
            try {
                call(part)
            } catch (e: RuntimeException) {
                Log.w("mpv", "pendengar pemutar gagal: ${part.javaClass.simpleName}", e)
            }
        }
    }
}
