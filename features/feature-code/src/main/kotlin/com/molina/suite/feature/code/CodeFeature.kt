package com.molina.suite.feature.code

import androidx.fragment.app.Fragment
import com.molina.suite.core.common.EngineFeature
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.EngineStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Implementasi kontrak [EngineFeature] untuk tab Code (engine: code-server). */
internal class CodeFeature(engine: CodeEngine) : EngineFeature {

    override val id: EngineId = EngineId.CODE

    private val mutableStatus = MutableStateFlow(statusFor(engine.installer.state.value))
    override val status: StateFlow<EngineStatus> = mutableStatus.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            engine.installer.state.collect { mutableStatus.value = statusFor(it) }
        }
    }

    override fun createFragment(): Fragment = CodeTabFragment()
}

// Status STOPPED/RUNNING untuk server ditambahkan pada tahap berikutnya.
private fun statusFor(state: CodeInstallState): EngineStatus = when (state) {
    CodeInstallState.RootfsMissing -> EngineStatus.UNAVAILABLE
    is CodeInstallState.NotInstalled -> EngineStatus.UNAVAILABLE
    is CodeInstallState.Installing -> EngineStatus.UNAVAILABLE
    is CodeInstallState.Installed -> EngineStatus.STOPPED
    is CodeInstallState.Failed -> EngineStatus.ERROR
}
