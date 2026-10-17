package com.molina.suite.feature.mpv

/**
 * Ekstensi yang ditawarkan sebagai "putar di mpv" dari modul lain. Sengaja lebih sempit daripada
 * daftar pemilih berkas mpv: ts, mod, dan sejenisnya dikecualikan karena di editor kode berarti
 * TypeScript atau berkas teks, bukan video.
 */
internal object MediaFileNames {

    private val PLAYLIST = setOf("m3u", "m3u8", "pls")
    private val AUDIO = setOf(
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "mka", "aif", "aiff", "amr", "ac3"
    )
    private val VIDEO = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "flv", "wmv", "m4v", "3gp", "mpg", "mpeg", "ogv", "m2ts",
        "vob", "asf", "f4v", "rmvb"
    )
    private val PLAYABLE = PLAYLIST + AUDIO + VIDEO

    fun isPlayable(name: String): Boolean {
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return false
        return name.substring(dot + 1).lowercase() in PLAYABLE
    }
}
