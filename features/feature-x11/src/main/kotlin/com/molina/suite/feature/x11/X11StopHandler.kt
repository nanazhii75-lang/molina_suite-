package com.molina.suite.feature.x11

import com.molina.suite.core.common.X11Controller

/** Penghubung receiver notifikasi ke pengendali yang diberikan shell saat instalasi modul. */
internal object X11StopHandler {
    @Volatile
    var controller: X11Controller? = null
}
