package com.molina.suite.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Papan status daemon. Layanan (terminal, WebDAV, Kodi) mempublikasikan status,
 * UI hanya mengamati. Tidak ada referensi langsung antar-modul.
 */
class DaemonStatusBoard {
    private val states = DaemonId.values().associateWith {
        MutableStateFlow(EngineStatus.UNAVAILABLE)
    }

    fun status(id: DaemonId): StateFlow<EngineStatus> = states.getValue(id).asStateFlow()

    fun publish(id: DaemonId, status: EngineStatus) {
        states.getValue(id).value = status
    }
}
