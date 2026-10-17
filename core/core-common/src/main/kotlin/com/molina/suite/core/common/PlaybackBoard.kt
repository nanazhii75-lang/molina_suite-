package com.molina.suite.core.common

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Potret pemutaran yang sedang berjalan untuk mini-player di shell. Hanya hidup di dalam proses
 * aplikasi; tidak memuat URL maupun jalur berkas.
 */
data class PlaybackSnapshot(
    val title: String,
    val paused: Boolean,
    val positionSec: Long,
    val durationSec: Long,
    val hasPlaylist: Boolean
)

/** Kendali pemutar yang dipakai mini-player. Diimplementasi oleh modul mpv, shell hanya mengenal interface ini. */
interface PlaybackControls {
    fun togglePause()
    fun previous()
    fun next()

    /** Membawa jendela pemutar kembali ke depan. */
    fun openPlayer(host: Activity)

    /** Menutup pemutar dan menghentikan pemutaran. */
    fun close()
}

/**
 * Papan status pemutaran. Modul mpv mempublikasikan [snapshot] (null saat tidak ada pemutaran) dan
 * memasang [PlaybackControls]; shell hanya mengamati dan memanggil kendali.
 */
class PlaybackBoard {
    private val state = MutableStateFlow<PlaybackSnapshot?>(null)

    @Volatile
    private var handler: PlaybackControls? = null

    val snapshot: StateFlow<PlaybackSnapshot?> = state.asStateFlow()

    val controls: PlaybackControls? get() = handler

    fun publish(snapshot: PlaybackSnapshot?) {
        state.value = snapshot
    }

    fun setControls(controls: PlaybackControls?) {
        handler = controls
    }
}
