package com.molina.suite.feature.settings.export

import com.molina.suite.core.common.ConversionRequest
import com.molina.suite.core.common.DocumentFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XlsxCommandBuilderTest {

    private fun request(
        input: String = "/sdcard/Molina/data.xlsx",
        output: String = "/sdcard/Molina/Export/data.md"
    ) = ConversionRequest(input, output, DocumentFormat.XLSX, DocumentFormat.MARKDOWN)

    @Test
    fun skripMemakaiKonverterPython() {
        val script = XlsxCommandBuilder.script(request(), "print('x')")
        assertNotNull(script)
        assertTrue(script!!.contains("python3 -c"))
        assertTrue(script.contains("'data.xlsx'"))
        assertTrue(script.contains("mv -f"))
    }

    @Test
    fun jalurTidakAmanAtauKonverterKosongDitolak() {
        assertNull(XlsxCommandBuilder.script(request(input = "/data/data/x/a.xlsx"), "x"))
        assertNull(XlsxCommandBuilder.script(request(input = "/sdcard/../a.xlsx"), "x"))
        assertNull(XlsxCommandBuilder.script(request(output = "/sdcard/Molina/Export/data.txt"), "x"))
        assertNull(XlsxCommandBuilder.script(request(), null))
        assertNull(XlsxCommandBuilder.script(request(), ""))
    }

    @Test
    fun hanyaMarkdownYangDiizinkan() {
        assertEquals(listOf(DocumentFormat.MARKDOWN), XlsxCommandBuilder.TARGETS)
        assertEquals(listOf("python"), XlsxCommandBuilder.requiredTools())
    }

    @Test(expected = IllegalArgumentException::class)
    fun xlsxKeHtmlDitolakKontrak() {
        ConversionRequest("/sdcard/a.xlsx", "/sdcard/a.html", DocumentFormat.XLSX, DocumentFormat.HTML)
    }

    @Test(expected = IllegalArgumentException::class)
    fun xlsxTidakBisaJadiHasil() {
        ConversionRequest("/sdcard/a.md", "/sdcard/a.xlsx", DocumentFormat.MARKDOWN, DocumentFormat.XLSX)
    }
}
