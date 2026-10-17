package com.molina.suite.feature.x11

/** Aturan kapan X11 dihentikan otomatis: aplikasi di latar belakang sedikitnya sebanyak menit yang dipilih. */
internal object X11IdlePolicy {

    private const val MILLIS_PER_MINUTE = 60_000L

    /** [backgroundSince] null berarti aplikasi sedang di depan; [minutes] nol atau kurang berarti mati. */
    fun shouldStop(minutes: Int, backgroundSince: Long?, now: Long): Boolean {
        if (minutes <= 0 || backgroundSince == null) return false
        return now - backgroundSince >= minutes * MILLIS_PER_MINUTE
    }
}
