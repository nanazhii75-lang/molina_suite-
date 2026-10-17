package com.molina.suite.core.common

/**
 * Lintasan integrasi antar-modul yang bisa dinyalakan atau dimatikan pengguna di Setting.
 * [engines] adalah engine yang harus tersedia, [scripts] perintah Terminal di usr/bin, dan
 * [entryClasses] komponen privat aplikasi yang dituju; ketiganya dipakai pemeriksa di Setting.
 * [key] disimpan di preferensi dan tidak boleh berubah diam-diam.
 */
enum class IntegrationRoute(
    val key: String,
    val engines: List<EngineId>,
    val scripts: List<String>,
    val entryClasses: List<String>
) {
    TERMINAL_TO_CODE(
        "terminal_to_code",
        listOf(EngineId.TERMINAL, EngineId.CODE),
        listOf("molina-open"),
        listOf("com.molina.suite.feature.code.MolinaOpenActivity")
    ),
    TERMINAL_TO_MPV(
        "terminal_to_mpv",
        listOf(EngineId.TERMINAL, EngineId.MPV),
        listOf("molina-play", "molina-cari"),
        listOf("com.molina.suite.feature.mpv.MolinaCommandActivity")
    ),
    CODE_TO_MPV(
        "code_to_mpv",
        listOf(EngineId.CODE, EngineId.MPV),
        emptyList(),
        listOf("com.molina.suite.mpv.MPVActivity")
    )
}
