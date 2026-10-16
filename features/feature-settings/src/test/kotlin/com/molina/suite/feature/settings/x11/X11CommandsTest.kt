package com.molina.suite.feature.settings.x11

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class X11CommandsTest {

    @Test
    fun jalankanDesktopMemakaiTmpBersamaDanBashInteraktif() {
        val line = X11Commands.startDesktop()
        assertTrue(line.contains("proot-distro login debian --shared-tmp -- bash -ic 'molina-x11 desktop'"))
        assertTrue(line.contains("then molina-x11 desktop; elif"))
    }

    @Test
    fun hentikanDesktopMemakaiPerintahStop() {
        val line = X11Commands.stopDesktop()
        assertTrue(line.contains("'molina-x11 stop'"))
    }

    @Test
    fun barisSelaluSatuBaris() {
        assertFalse(X11Commands.startDesktop().any { it == '\n' || it == '\r' })
        assertFalse(X11Commands.stopDesktop().any { it == '\n' || it == '\r' })
    }

    @Test
    fun tanpaDebianMencetakPetunjuk() {
        assertEquals(true, X11Commands.startDesktop().endsWith("Setting, bagian Debian.'; fi"))
    }
}
