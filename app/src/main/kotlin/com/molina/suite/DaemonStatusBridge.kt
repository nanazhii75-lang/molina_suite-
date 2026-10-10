package com.molina.suite

import com.molina.suite.core.common.DaemonId
import com.molina.suite.core.common.DaemonStatusBoard
import com.molina.suite.core.common.EngineId
import com.molina.suite.core.common.EngineRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Menerjemahkan status tiap engine ke papan daemon untuk header. Engine hanya
 * mengenal status miliknya; papan daemon hanya dikenal oleh app.
 */
object DaemonStatusBridge {

    private val daemonFor = mapOf(
        EngineId.TERMINAL to DaemonId.TERMINAL,
        EngineId.MPV to DaemonId.MPV,
        EngineId.CODE to DaemonId.CODE
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun start(engines: EngineRegistry, daemons: DaemonStatusBoard) {
        for ((engineId, daemonId) in daemonFor) {
            val feature = engines.find(engineId) ?: continue
            scope.launch {
                feature.status.collect { daemons.publish(daemonId, it) }
            }
        }
    }
}
