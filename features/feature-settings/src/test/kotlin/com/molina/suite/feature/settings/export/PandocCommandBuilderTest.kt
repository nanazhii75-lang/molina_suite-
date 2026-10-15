package com.molina.suite.feature.settings.export

import com.molina.suite.core.common.ConversionRequest
import com.molina.suite.core.common.DocumentFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PandocCommandBuilderTest {

    @Test
    fun docxKeMarkdownMengekstrakGambarDiSampingHasil() {
        val script = PandocCommandBuilder.script(
            ConversionRequest(
                "/sdcard/Molina/Laporan Q3.docx", "/sdcard/Molina/Export/Laporan Q3.md",
                DocumentFormat.DOCX, DocumentFormat.MARKDOWN
            )
        )
        assertNotNull(script)
        assertTrue(script!!.contains("M='Laporan_Q3_media'"))
        assertTrue(script.contains("--extract-media=\"\$M\""))
        assertTrue(script.contains("cd '/sdcard/Molina/Export'"))
    }

    @Test
    fun konversiLainTidakMengekstrakGambar() {
        val script = PandocCommandBuilder.script(
            ConversionRequest(
                "/sdcard/Molina/a.md", "/sdcard/Molina/Export/a.docx",
                DocumentFormat.MARKDOWN, DocumentFormat.DOCX
            )
        )
        assertNotNull(script)
        assertFalse(script!!.contains("--extract-media"))
    }

    @Test
    fun namaFolderGambarAman() {
        assertEquals("Laporan_Q3_media", PandocCommandBuilder.mediaDirName("Laporan Q3"))
        assertEquals("dokumen_media", PandocCommandBuilder.mediaDirName("..."))
        assertEquals("a_b__media", PandocCommandBuilder.mediaDirName("a'b\$"))
    }
}
