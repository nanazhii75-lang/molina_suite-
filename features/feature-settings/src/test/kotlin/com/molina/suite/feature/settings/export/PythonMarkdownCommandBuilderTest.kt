package com.molina.suite.feature.settings.export

import com.molina.suite.core.common.ConversionRequest
import com.molina.suite.core.common.DocumentFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonMarkdownCommandBuilderTest {

    private fun request(
        input: String = "/sdcard/Molina/data.xlsx",
        output: String = "/sdcard/Molina/Export/data.md",
        source: DocumentFormat = DocumentFormat.XLSX
    ) = ConversionRequest(input, output, source, DocumentFormat.MARKDOWN)

    @Test
    fun skripMemakaiKonverterPython() {
        val script = PythonMarkdownCommandBuilder.script(request(), PythonMarkdownSpec.SPREADSHEET, "print('x')")
        assertNotNull(script)
        assertTrue(script!!.contains("python3 -c"))
        assertTrue(script.contains("'data.xlsx'"))
        assertTrue(script.contains("mv -f"))
    }

    @Test
    fun odsDitanganiSpreadsheetDanPptxDitanganiPresentasi() {
        assertNotNull(
            PythonMarkdownCommandBuilder.script(
                request("/sdcard/a.ods", "/sdcard/a.md", DocumentFormat.ODS), PythonMarkdownSpec.SPREADSHEET, "x"
            )
        )
        assertNotNull(
            PythonMarkdownCommandBuilder.script(
                request("/sdcard/a.pptx", "/sdcard/a.md", DocumentFormat.PPTX), PythonMarkdownSpec.PRESENTATION, "x"
            )
        )
    }

    @Test
    fun sumberBukanMilikSpecDitolak() {
        assertNull(
            PythonMarkdownCommandBuilder.script(
                request("/sdcard/a.pptx", "/sdcard/a.md", DocumentFormat.PPTX), PythonMarkdownSpec.SPREADSHEET, "x"
            )
        )
    }

    @Test
    fun jalurTidakAmanAtauKonverterKosongDitolak() {
        val spec = PythonMarkdownSpec.SPREADSHEET
        assertNull(PythonMarkdownCommandBuilder.script(request(input = "/data/data/x/a.xlsx"), spec, "x"))
        assertNull(PythonMarkdownCommandBuilder.script(request(input = "/sdcard/../a.xlsx"), spec, "x"))
        assertNull(PythonMarkdownCommandBuilder.script(request(output = "/sdcard/Molina/Export/data.txt"), spec, "x"))
        assertNull(PythonMarkdownCommandBuilder.script(request(), spec, null))
        assertNull(PythonMarkdownCommandBuilder.script(request(), spec, ""))
    }

    @Test
    fun hanyaMarkdownDanKontrakMenolakSelainnya() {
        assertEquals(listOf(DocumentFormat.MARKDOWN), PythonMarkdownCommandBuilder.TARGETS)
        assertEquals(listOf("python"), PythonMarkdownCommandBuilder.requiredTools())
        for (source in listOf(DocumentFormat.XLSX, DocumentFormat.ODS, DocumentFormat.PPTX)) {
            assertEquals(setOf(DocumentFormat.MARKDOWN), source.restrictedTargets)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun pptxKeHtmlDitolakKontrak() {
        ConversionRequest("/sdcard/a.pptx", "/sdcard/a.html", DocumentFormat.PPTX, DocumentFormat.HTML)
    }

    @Test(expected = IllegalArgumentException::class)
    fun odsTidakBisaJadiHasil() {
        ConversionRequest("/sdcard/a.md", "/sdcard/a.ods", DocumentFormat.MARKDOWN, DocumentFormat.ODS)
    }
}
