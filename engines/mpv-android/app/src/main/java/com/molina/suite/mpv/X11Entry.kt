package com.molina.suite.mpv

import android.app.Activity

/** Pembuka jendela X11 yang dipasang oleh shell; modul mpv tidak mengenal engine X11. */
fun interface X11Opener {
    /** @return true bila permintaan buka diteruskan, false bila X11 tidak tersedia. */
    fun open(host: Activity): Boolean
}

object X11Entry {
    @Volatile
    private var opener: X11Opener? = null

    fun install(opener: X11Opener) {
        this.opener = opener
    }

    fun open(host: Activity): Boolean = opener?.open(host) ?: false
}
