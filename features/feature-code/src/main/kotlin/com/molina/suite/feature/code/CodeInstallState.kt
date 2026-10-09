package com.molina.suite.feature.code

internal enum class InstallPhase {
    PREPARE,
    DOWNLOAD,
    VERIFY,
    EXTRACT,
    FINISH
}

/** Keadaan pemasangan code-server yang ditampilkan tab Code. */
internal sealed interface CodeInstallState {
    object RootfsMissing : CodeInstallState

    data class NotInstalled(val downloadBytes: Long) : CodeInstallState

    data class Installing(
        val phase: InstallPhase,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : CodeInstallState

    data class Installed(val version: String) : CodeInstallState

    data class Failed(val reason: String) : CodeInstallState
}
