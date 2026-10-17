package com.molina.suite.feature.mpv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFileNamesTest {

    @Test
    fun videoAudioDanPlaylistBisaDiputar() {
        listOf("a.mp4", "B.MKV", "lagu.mp3", "radio.m3u8", "iptv.m3u", "x.flac", "klip.webm")
            .forEach { assertTrue(it, MediaFileNames.isPlayable(it)) }
    }

    @Test
    fun berkasKodeTidakDitawari() {
        listOf("app.ts", "main.py", "catatan.txt", "index.html", "a.mod", "Makefile", "tanpa.", ".mp4x")
            .forEach { assertFalse(it, MediaFileNames.isPlayable(it)) }
    }
}
