package com.molina.suite.feature.code

import androidx.fragment.app.Fragment
import com.molina.suite.core.common.DaemonId
import com.molina.suite.core.common.DaemonStatusBoard
import com.molina.suite.core.common.EngineFeature
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.EngineStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Implementasi kontrak [EngineFeature] untuk tab Code (engine: code-server). */
internal class CodeFeature(engine: CodeEngine, daemons: DaemonStatusBoard) : EngineFeature {

    override val id: EngineId = EngineId.CODE

    private val mutableStatus = MutableStateFlow(
        statusFor(engine.installer.state.value, engine.controller.state.value)
    )
    override val status: StateFlow<EngineStatus> = mutableStatus.asStateFlow()

    init {
        daemons.publish(DaemonId.CODE, mutableStatus.value)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            combine(engine.installer.state, engine.controller.state) { install, server ->
                statusFor(install, server)
            }.collect { value ->
                mutableStatus.value = value
                daemons.publish(DaemonId.CODE, value)
            }
        }
    }

    override fun createFragment(): Fragment = CodeTabFragment()
}

private fun statusFor(install: CodeInstallState, server: CodeServerState): EngineStatus =
    when (install) {
        CodeInstallState.RootfsMissing -> EngineStatus.UNAVAILABLE
        is CodeInstallState.NotInstalled -> EngineStatus.UNAVAILABLE
        is CodeInstallState.Installing -> EngineStatus.UNAVAILABLE
        is CodeInstallState.Failed -> EngineStatus.ERROR
        is CodeInstallState.Installed -> when (server) {
            CodeServerState.Stopped -> EngineStatus.STOPPED
            CodeServerState.Stopping -> EngineStatus.STOPPED
            CodeServerState.Starting -> EngineStatus.STARTING
            CodeServerState.Running -> EngineStatus.RUNNING
            is CodeServerState.Failed -> EngineStatus.ERROR
        }
    }
