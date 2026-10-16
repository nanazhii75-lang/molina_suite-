package com.molina.suite.feature.settings.export

import com.molina.suite.core.common.ConversionOptions
import com.molina.suite.core.common.ConversionRequest
import com.molina.suite.core.common.DocumentFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfCommandBuilderTest {

    private fun request(
        input: String = "/sdcard/Molina/a.pdf",
        output: String = "/sdcard/Molina/Export/a.md",
        target: DocumentFormat = DocumentFormat.MARKDOWN,
        options: ConversionOptions = ConversionOptions()
    ) = ConversionRequest(input, output, DocumentFormat.PDF, target, options)

    @Test
    fun markdownMemakaiPdftohtmlDanKonverter() {
        val script = PdfCommandBuilder.script(request(), "print('x')")
        assertNotNull(script)
        assertTrue(script!!.contains("pdftohtml -xml"))
        assertTrue(script.contains("python3 -c"))
        assertFalse(script.contains("--keep-headers"))
        assertFalse(script.contains("--page-breaks"))
    }

    @Test
    fun opsiDiteruskanSebagaiFlag() {
        val options = ConversionOptions(stripRepeatedHeaders = false, pageBreaks = true)
        val script = PdfCommandBuilder.script(request(options = options), "print('x')")!!
        assertTrue(script.contains("--keep-headers"))
        assertTrue(script.contains("--page-breaks"))
    }

    @Test
    fun teksBiasaMemakaiPdftotextTanpaKonverter() {
        val script = PdfCommandBuilder.script(
            request(output = "/sdcard/Molina/Export/a.txt", target = DocumentFormat.PLAIN), null
        )
        assertNotNull(script)
        assertTrue(script!!.contains("pdftotext"))
        assertFalse(script.contains("python3"))
    }

    @Test
    fun jalurTidakAmanDitolak() {
        assertNull(PdfCommandBuilder.script(request(input = "/data/data/x/a.pdf"), "x"))
        assertNull(PdfCommandBuilder.script(request(input = "/sdcard/../a.pdf"), "x"))
        assertNull(PdfCommandBuilder.script(request(input = "/sdcard/Molina/a\nb.pdf"), "x"))
        assertNull(PdfCommandBuilder.script(request(output = "/sdcard/Molina/Export/a.txt"), "x"))
        assertNull(PdfCommandBuilder.script(request(output = "/sdcard/Molina/a.pdf"), "x"))
    }

    @Test
    fun markdownTanpaKonverterDitolak() {
        assertNull(PdfCommandBuilder.script(request(), null))
        assertNull(PdfCommandBuilder.script(request(), "  "))
    }

    @Test
    fun namaBerkasAneh_diKutipAman() {
        val script = PdfCommandBuilder.script(
            request(input = "/sdcard/Molina/-a 'b' \$x.pdf", output = "/sdcard/Molina/Export/-a 'b' \$x.md"), "x"
        )!!
        assertTrue(script.contains("'./-a '\\''b'\\'' \$x.pdf'"))
    }

    @Test
    fun alatYangDibutuhkan() {
        assertEquals(listOf("poppler", "python"), PdfCommandBuilder.requiredTools(DocumentFormat.MARKDOWN))
        assertEquals(listOf("poppler"), PdfCommandBuilder.requiredTools(DocumentFormat.PLAIN))
    }

    @Test
    fun ocrHanyaDipakaiBilaTesseractTerpasang() {
        val script = PdfCommandBuilder.script(request(), "print('x')")!!
        assertTrue(script.contains("command -v tesseract"))
        assertTrue(script.contains("pdftohtml -xml -i"))
        assertTrue(script.contains("--ocr"))
    }

    @Test
    fun ocrBukanSyaratAlat() {
        assertFalse(PdfCommandBuilder.requiredTools(DocumentFormat.MARKDOWN).contains("ocr"))
        assertFalse(PdfCommandBuilder.requiredTools(DocumentFormat.PLAIN).contains("ocr"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun pdfKeDocxDitolakKontrak() {
        request(output = "/sdcard/Molina/Export/a.docx", target = DocumentFormat.DOCX)
    }
}
