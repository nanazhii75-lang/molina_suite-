package com.molina.suite.feature.mpv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandInputTest {

    @Test
    fun urlStreamDiterima() {
        assertEquals(
            PlayTarget("https://youtu.be/abc", PlaySource.URL),
            CommandInput.playTarget("  https://youtu.be/abc ")
        )
        assertEquals(PlaySource.URL, CommandInput.playTarget("HTTPS://contoh.id/a.m3u8")?.source)
        assertEquals(PlaySource.URL, CommandInput.playTarget("rtsp://10.0.0.2/cam")?.source)
    }

    @Test
    fun skemaBerbahayDitolak() {
        listOf("ytdl://x", "edl://a;b", "fd://3", "file:///sdcard/a.mp4", "lavf://x", "memory://abc")
            .forEach { assertNull(it, CommandInput.playTarget(it) { true }) }
    }

    @Test
    fun berkasLokalHanyaBilaAdaDanAbsolut() {
        assertEquals(
            PlayTarget("/sdcard/Molina/a.mp4", PlaySource.LOCAL),
            CommandInput.playTarget("/sdcard/Molina/a.mp4") { true }
        )
        assertNull(CommandInput.playTarget("/sdcard/Molina/a.mp4") { false })
        assertNull(CommandInput.playTarget("a.mp4") { true })
    }

    @Test
    fun masukanRusakDitolak() {
        assertNull(CommandInput.playTarget(null))
        assertNull(CommandInput.playTarget("   "))
        assertNull(CommandInput.playTarget("https://a b"))
        assertNull(CommandInput.playTarget("https://a\u0007b"))
        assertNull(CommandInput.playTarget("https://" + "a".repeat(3000)))
    }

    @Test
    fun kataKunciDirapikan() {
        assertEquals("halo dunia", CommandInput.searchQuery("  halo \t dunia \n"))
        assertEquals("ab", CommandInput.searchQuery("a\u0000b"))
    }

    @Test
    fun kataKunciKosongDitolakDanPanjangDipotong() {
        assertNull(CommandInput.searchQuery(null))
        assertNull(CommandInput.searchQuery(" \n\t "))
        assertEquals(200, CommandInput.searchQuery("x".repeat(500))?.length)
    }
}
