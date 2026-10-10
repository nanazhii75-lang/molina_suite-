package com.molina.suite.core.common

/** Kontrak opsional untuk shell: fragment tab meminta shell berpindah ke tab engine lain. */
interface HostTabSwitcher {
    fun showEngine(id: EngineId)
}
