package com.molina.suite.feature.settings.integration

import org.junit.Assert.assertEquals
import org.junit.Test

class IntegrationStatusTest {

    @Test
    fun semuaSiapBerartiReady() {
        assertEquals(IntegrationState.READY, IntegrationStatus.of(true, true, true, true))
    }

    @Test
    fun dimatikanPenggunaMenangAtasGalatLain() {
        assertEquals(IntegrationState.DISABLED, IntegrationStatus.of(false, false, false, false))
    }

    @Test
    fun galatPertamaYangDilaporkan() {
        assertEquals(IntegrationState.ENGINE_MISSING, IntegrationStatus.of(true, false, false, false))
        assertEquals(IntegrationState.SCRIPT_MISSING, IntegrationStatus.of(true, true, false, false))
        assertEquals(IntegrationState.COMPONENT_MISSING, IntegrationStatus.of(true, true, true, false))
    }
}
