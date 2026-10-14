package com.molina.suite.mpv

import android.content.Intent

/**
 * Asal sebuah pemutaran. Dibawa lewat intent dari UI mpv ke [MPVActivity] dan hanya berupa
 * jenisnya: nama berkas dan URL sengaja tidak ikut agar tidak bisa bocor ke log.
 */
enum class MpvOpenKind {
    LOCAL, URL, SEARCH, OTHER;

    companion object {
        const val EXTRA = "molina_open_kind"

        fun fromIntent(intent: Intent): MpvOpenKind {
            val name = intent.getStringExtra(EXTRA) ?: return OTHER
            return values().firstOrNull { it.name == name } ?: OTHER
        }
    }
}

/**
 * Pendengar kejadian pemutaran. Engine mpv tidak mengenal pencatat log apa pun; modul fitur
 * memasang pendengarnya lewat [MpvActivityReporter]. [session] mengenali satu pemutaran dari
 * [opened] sampai [closed]. Semua metode boleh dipanggil dari thread mana pun.
 */
interface MpvActivityListener {

    /** Pemutar dibuka untuk satu pemutaran baru. */
    fun opened(session: Long, kind: MpvOpenKind)

    /** Catatan selama pemutaran; [error] true untuk galat dari mpv. */
    fun note(session: Long, text: String, error: Boolean)

    /**
     * Pemutar ditutup. [played] true bila berkas sempat termuat; [failure] berisi galat pertama
     * dari mpv bila berkas tidak pernah termuat, selain itu null.
     */
    fun closed(session: Long, played: Boolean, failure: String?)
}

/** Titik pertemuan longgar antara pemutar mpv dan pencatat. Tanpa pendengar, tidak ada yang dicatat. */
object MpvActivityReporter {

    @Volatile
    private var listener: MpvActivityListener? = null

    fun install(newListener: MpvActivityListener) {
        listener = newListener
    }

    internal fun watch(kind: MpvOpenKind): MpvPlaybackWatcher? =
        listener?.let { MpvPlaybackWatcher(it, kind) }
}
