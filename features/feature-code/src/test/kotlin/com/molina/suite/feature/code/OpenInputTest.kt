package com.molina.suite.feature.code

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenInputTest {

    @Test
    fun jalurAbsolutDiterimaBilaBerkasAda() {
        assertEquals(
            File("/sdcard/Molina/a.py"),
            OpenInput.fileTarget("  /sdcard/Molina/a.py ") { true }
        )
    }

    @Test
    fun titikDanTitikDuaDinormalkan() {
        assertEquals(
            File("/sdcard/Molina/b.py"),
            OpenInput.fileTarget("/sdcard/Molina/x/../b.py") { true }
        )
    }

    @Test
    fun masukanTidakValidDitolak() {
        listOf(null, "", "   ", "a.py", "../a.py", "/sdcard/a\u0000b")
            .forEach { assertNull(it, OpenInput.fileTarget(it) { true }) }
    }

    @Test
    fun berkasTidakAdaDitolak() {
        assertNull(OpenInput.fileTarget("/sdcard/Molina/hilang.py") { false })
    }
}
