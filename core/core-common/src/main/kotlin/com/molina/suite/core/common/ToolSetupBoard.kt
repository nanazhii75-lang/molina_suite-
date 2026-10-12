package com.molina.suite.core.common

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Layar pemasangan yang diminta sebuah fitur. */
enum class ToolSetupTarget {
    /** Menu Paket; paket di [ToolSetupRequest.toolIds] disorot. */
    PACKAGES,

    /** Menu Debian. */
    DEBIAN
}

/** Permintaan membuka layar pemasangan; [serial] membedakan permintaan yang berurutan. */
data class ToolSetupRequest(
    val target: ToolSetupTarget,
    val toolIds: List<String>,
    val serial: Long
)

/**
 * Papan permintaan antar-modul: fitur mana pun (mis. pemutar mpv) meminta Settings membuka
 * menu pemasangan tanpa mengenal kelas Settings. Settings mengamati [pending] dan memanggil
 * [consume] setelah menanganinya.
 */
class ToolSetupBoard {

    private val counter = AtomicLong()
    private val mutablePending = MutableStateFlow<ToolSetupRequest?>(null)

    val pending: StateFlow<ToolSetupRequest?> = mutablePending.asStateFlow()

    fun request(target: ToolSetupTarget, toolIds: List<String>) {
        mutablePending.value = ToolSetupRequest(target, toolIds.toList(), counter.incrementAndGet())
    }

    /** Menandai [request] selesai ditangani; permintaan yang lebih baru tidak ikut terhapus. */
    fun consume(request: ToolSetupRequest) {
        mutablePending.compareAndSet(request, null)
    }
}
