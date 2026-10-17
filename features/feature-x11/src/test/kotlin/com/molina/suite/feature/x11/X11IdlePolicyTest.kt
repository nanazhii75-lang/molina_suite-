package com.molina.suite.feature.x11

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class X11IdlePolicyTest {

    private val minute = 60_000L

    @Test
    fun matiBerartiTidakPernahBerhenti() {
        assertFalse(X11IdlePolicy.shouldStop(0, 0L, 10_000 * minute))
    }

    @Test
    fun diDepanBerartiTidakBerhenti() {
        assertFalse(X11IdlePolicy.shouldStop(10, null, 10_000 * minute))
    }

    @Test
    fun belumSampaiBatasTidakBerhenti() {
        assertFalse(X11IdlePolicy.shouldStop(10, 0L, 10 * minute - 1))
    }

    @Test
    fun tepatDiBatasBerhenti() {
        assertTrue(X11IdlePolicy.shouldStop(10, 0L, 10 * minute))
    }

    @Test
    fun lewatBatasBerhenti() {
        assertTrue(X11IdlePolicy.shouldStop(30, 5 * minute, 40 * minute))
    }
}
