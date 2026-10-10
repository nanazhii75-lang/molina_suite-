package com.molina.suite.feature.code

/** Keadaan proses code-server (terpisah dari keadaan pemasangan). */
internal sealed interface CodeServerState {
    object Stopped : CodeServerState

    object Starting : CodeServerState

    object Running : CodeServerState

    object Stopping : CodeServerState

    data class Failed(val reason: String) : CodeServerState
}
