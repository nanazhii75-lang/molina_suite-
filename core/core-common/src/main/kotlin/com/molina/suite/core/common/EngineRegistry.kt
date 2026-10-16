package com.molina.suite.core.common

import java.util.concurrent.ConcurrentHashMap

/** Registri engine yang aman antar-thread. Engine mendaftar sekali saat startup. */
class EngineRegistry {
    private val features = ConcurrentHashMap<EngineId, EngineFeature>()
    private val launchers = ConcurrentHashMap<EngineId, EngineLauncher>()

    fun register(feature: EngineFeature) {
        val previous = features.putIfAbsent(feature.id, feature)
        check(previous == null) { "Engine ${feature.id} sudah terdaftar" }
    }

    fun find(id: EngineId): EngineFeature? = features[id]

    /** Mendaftarkan engine tanpa tab (jendela sendiri). */
    fun registerLauncher(launcher: EngineLauncher) {
        val previous = launchers.putIfAbsent(launcher.id, launcher)
        check(previous == null) { "Peluncur ${launcher.id} sudah terdaftar" }
    }

    fun findLauncher(id: EngineId): EngineLauncher? = launchers[id]
}
