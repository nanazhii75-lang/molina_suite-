package com.molina.suite.feature.code

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeFilePlayEnabledTest {

    private val file = File("/sdcard/Molina/Playlists/iptv.m3u8")

    @Test
    fun lintasanMenyalaMenawarkanPutar() {
        val a = CodeFileActions.available(file, canExport = false, hasContent = false, canPlay = true, playEnabled = true)
        assertTrue(CodeFileAction.PLAY_MPV in a)
        assertFalse(CodeFileAction.PLAY_MPV_OFF in a)
    }

    @Test
    fun lintasanMatiMenawarkanPintasanKeSetting() {
        val a = CodeFileActions.available(file, canExport = false, hasContent = false, canPlay = true, playEnabled = false)
        assertTrue(CodeFileAction.PLAY_MPV_OFF in a)
        assertFalse(CodeFileAction.PLAY_MPV in a)
    }

    @Test
    fun berkasBukanMediaTidakMenawarkanApaPun() {
        val a = CodeFileActions.available(File("/sdcard/Molina/a.py"), canExport = false, hasContent = false, canPlay = false, playEnabled = false)
        assertFalse(CodeFileAction.PLAY_MPV in a)
        assertFalse(CodeFileAction.PLAY_MPV_OFF in a)
    }
}
