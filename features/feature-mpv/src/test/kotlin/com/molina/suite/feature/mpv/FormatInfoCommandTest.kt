package com.molina.suite.feature.mpv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatInfoCommandTest {

    @Test
    fun urlYoutubeDikutipUtuh() {
        assertEquals(
            "molina-ytdlp --no-playlist -F 'https://youtu.be/abc'",
            FormatInfoCommand.build("  https://youtu.be/abc ")
        )
    }

    @Test
    fun tandaShellTidakJadiSintaks() {
        assertEquals(
            "molina-ytdlp --no-playlist -F 'https://x.id/w?v=1&t=2;rm$(id)`x`'",
            FormatInfoCommand.build("https://x.id/w?v=1&t=2;rm$(id)`x`")
        )
    }

    @Test
    fun kutipTunggalDiloloskan() {
        assertEquals("'a'\\''b'", FormatInfoCommand.quote("a'b"))
    }

    @Test
    fun skemaLainDanKarakterKendaliDitolak() {
        listOf(
            null, "", "   ", "/sdcard/a.mp4", "file:///sdcard/a.mp4", "ytdl://x", "edl://a;b",
            "rtsp://10.0.0.2/cam", "https://a.id/x y", "https://a.id/x\ny", "https://a.id/\u0000"
        ).forEach { assertNull(it, FormatInfoCommand.build(it)) }
    }

    @Test
    fun urlTerlaluPanjangDitolak() {
        assertNull(FormatInfoCommand.build("https://a.id/" + "x".repeat(2100)))
    }
}
