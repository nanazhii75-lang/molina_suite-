package com.molina.suite.feature.settings.integration

/** Keadaan satu lintasan integrasi, dari yang paling menentukan ke yang paling teknis. */
internal enum class IntegrationState { READY, DISABLED, ENGINE_MISSING, SCRIPT_MISSING, COMPONENT_MISSING }

internal object IntegrationStatus {

    /** Dimatikan pengguna menang atas semua; selebihnya galat pertama yang ditemukan. */
    fun of(
        enabled: Boolean,
        enginesReady: Boolean,
        scriptsReady: Boolean,
        componentsReady: Boolean
    ): IntegrationState = when {
        !enabled -> IntegrationState.DISABLED
        !enginesReady -> IntegrationState.ENGINE_MISSING
        !scriptsReady -> IntegrationState.SCRIPT_MISSING
        !componentsReady -> IntegrationState.COMPONENT_MISSING
        else -> IntegrationState.READY
    }
}
