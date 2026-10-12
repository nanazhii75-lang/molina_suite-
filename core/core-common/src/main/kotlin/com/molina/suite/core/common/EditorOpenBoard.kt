package com.molina.suite.core.common

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Permintaan membuka berkas di tab Code; [serial] membedakan permintaan yang berurutan. */
data class EditorOpenRequest(val path: String, val serial: Long)

/**
 * Papan permintaan antar-modul: fitur mana pun (mis. Library di Settings) meminta tab Code
 * membuka sebuah berkas tanpa mengenal kelas editor. Tab Code mengamati [pending] dan memanggil
 * [consume] setelah menanganinya. Permintaan yang datang sebelum tab Code dibuat tetap
 * tersimpan sampai dikonsumsi.
 */
class EditorOpenBoard {

    private val counter = AtomicLong()
    private val mutablePending = MutableStateFlow<EditorOpenRequest?>(null)

    val pending: StateFlow<EditorOpenRequest?> = mutablePending.asStateFlow()

    fun request(path: String) {
        mutablePending.value = EditorOpenRequest(path, counter.incrementAndGet())
    }

    /** Menandai [request] selesai ditangani; permintaan yang lebih baru tidak ikut terhapus. */
    fun consume(request: EditorOpenRequest) {
        mutablePending.compareAndSet(request, null)
    }
}
