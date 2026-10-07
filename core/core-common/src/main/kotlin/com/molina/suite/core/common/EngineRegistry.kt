package com.molina.suite.core.common

import java.util.concurrent.ConcurrentHashMap

/** Registri engine yang aman antar-thread. Engine mendaftar sekali saat startup. */
class EngineRegistry {
    private val features = ConcurrentHashMap<EngineId, EngineFeature>()

    fun register(feature: EngineFeature) {
        val previous = features.putIfAbsent(feature.id, feature)
        check(previous == null) { "Engine ${feature.id} sudah terdaftar" }
    }

    fun find(id: EngineId): EngineFeature? = features[id]
}
