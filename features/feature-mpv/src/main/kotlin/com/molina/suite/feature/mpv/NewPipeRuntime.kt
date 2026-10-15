package com.molina.suite.feature.mpv

import org.schabi.newpipe.extractor.NewPipe

/** Menginisialisasi NewPipeExtractor sekali, saat pencarian pertama, bukan saat aplikasi mulai. */
internal object NewPipeRuntime {

    private val lock = Any()
    private var ready = false

    fun ensureReady() {
        synchronized(lock) {
            if (ready) return
            NewPipe.init(NewPipeHttpDownloader.create())
            ready = true
        }
    }
}
