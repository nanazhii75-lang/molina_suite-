package com.molina.suite.core.common

import android.app.Activity
import java.io.File

/** Hasil [MediaOpener.open]. */
sealed interface MediaOpenResult {
    /** Pemutar dibuka untuk berkas itu. */
    object Started : MediaOpenResult

    /** Berkas tidak diputar; [reason] siap ditampilkan ke pengguna. */
    data class Rejected(val reason: String) : MediaOpenResult
}

/**
 * Kontrak antara modul fitur (mis. Code) dan pemutar media untuk memutar berkas lokal tanpa
 * mengenal kelas pemutar. Diimplementasi oleh modul mpv.
 */
interface MediaOpener {

    /** True bila [file] berekstensi media yang bisa diputar. Hanya memeriksa nama, tanpa akses disk. */
    fun canOpen(file: File): Boolean

    /** Membuka pemutar untuk [file] dari [host]. Berkas harus ada dan terbaca. */
    fun open(host: Activity, file: File): MediaOpenResult
}
