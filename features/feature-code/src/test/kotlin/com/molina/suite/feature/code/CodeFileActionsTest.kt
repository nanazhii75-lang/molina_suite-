package com.molina.suite.feature.code

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CodeFileActionsTest {

    private fun actions(name: String, canExport: Boolean = false, hasContent: Boolean = false) =
        CodeFileActions.available(File("/sdcard/Molina/$name"), canExport, hasContent)

    @Test
    fun tanpaBerkasTidakAdaAksi() {
        assertTrue(CodeFileActions.available(null, canExport = true, hasContent = true).isEmpty())
    }

    @Test
    fun jsHanyaBisaJalanDiDebian() {
        val a = actions("t.js")
        assertTrue(CodeFileAction.RUN_DEBIAN in a)
        assertFalse(CodeFileAction.RUN_HOST in a)
    }

    @Test
    fun pyBisaJalanDiDebianDanHost() {
        val a = actions("a.py")
        assertTrue(CodeFileAction.RUN_DEBIAN in a)
        assertTrue(CodeFileAction.RUN_HOST in a)
    }

    @Test
    fun shBisaJalanDiDebianDanHost() {
        val a = actions("run.sh")
        assertTrue(CodeFileAction.RUN_DEBIAN in a)
        assertTrue(CodeFileAction.RUN_HOST in a)
    }

    @Test
    fun berkasTeksTidakPunyaAksiJalankan() {
        val a = actions("catatan.txt")
        assertFalse(CodeFileAction.RUN_DEBIAN in a)
        assertFalse(CodeFileAction.RUN_HOST in a)
    }

    @Test
    fun ekspordanLibraryMengikutiBendera() {
        val tanpa = actions("a.py", canExport = false, hasContent = false)
        assertFalse(CodeFileAction.EXPORT in tanpa)
        assertFalse(CodeFileAction.LIBRARY in tanpa)

        val dengan = actions("a.py", canExport = true, hasContent = true)
        assertTrue(CodeFileAction.EXPORT in dengan)
        assertTrue(CodeFileAction.LIBRARY in dengan)
    }

    @Test
    fun aksiPeriksaAdaUntukBahasaYangDikenali() {
        assertTrue(CodeFileAction.CHECK in actions("t.js"))
        assertTrue(CodeFileAction.CHECK in actions("a.py"))
    }

    @Test
    fun urutanAksiTetap() {
        val a = actions("a.py", canExport = true, hasContent = true)
        val urutan = a.map { it.ordinal }
        assertEquals("urutan harus naik sesuai deklarasi enum", urutan.sorted(), urutan)
    }

    @Test
    fun tidakAdaAksiGanda() {
        val a = actions("a.py", canExport = true, hasContent = true)
        assertEquals(a.size, a.toSet().size)
    }
}
