package com.molina.suite.mpv

import java.util.concurrent.ConcurrentHashMap

/**
 * Titik tambah opsi mpv untuk host. Modul pemakai mengisi opsi sebelum pemutar
 * dibuat; [MPVView] menerapkannya saat inisialisasi. Engine tidak mengenal
 * sumber opsi, sehingga tidak ada dependensi ke modul lain.
 */
object MolinaMpvOptions {

    private val options = ConcurrentHashMap<String, String>()

    fun set(name: String, value: String) {
        options[name] = value
    }

    fun snapshot(): Map<String, String> = HashMap(options)
}
