package com.molina.suite.core.common

/** Identitas tiap tab/engine pada shell molina-suite. */
enum class EngineId {
    MPV,
    TERMINAL,
    CODE,
    SETTINGS,

    /** X server Molina X11. Tidak punya tab; diluncurkan dari home mpv. */
    X11
}
