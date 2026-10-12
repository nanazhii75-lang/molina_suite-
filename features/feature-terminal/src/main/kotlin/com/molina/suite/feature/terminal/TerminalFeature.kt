package com.molina.suite.feature.terminal

import androidx.fragment.app.Fragment
import com.molina.suite.core.common.EngineFeature
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.EngineStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Implementasi kontrak [EngineFeature] untuk tab Terminal (engine: Termux). */
class TerminalFeature : EngineFeature {

    override val id: EngineId = EngineId.TERMINAL

    // Pelacakan status service berjalan/berhenti ditambahkan pada tahap daemon (1d).
    private val mutableStatus = MutableStateFlow(EngineStatus.STOPPED)
    override val status: StateFlow<EngineStatus> = mutableStatus.asStateFlow()

    override fun createFragment(): Fragment = TerminalTabFragment()
}
