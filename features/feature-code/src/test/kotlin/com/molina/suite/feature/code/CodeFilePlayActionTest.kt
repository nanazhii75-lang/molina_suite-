package com.molina.suite.feature.code

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeFilePlayActionTest {

    private val file = File("/sdcard/Molina/Playlists/iptv.m3u8")

    @Test
    fun aksiPutarMengikutiBendera() {
        assertTrue(
            CodeFileAction.PLAY_MPV in
                CodeFileActions.available(file, canExport = false, hasContent = false, canPlay = true)
        )
        assertFalse(
            CodeFileAction.PLAY_MPV in
                CodeFileActions.available(file, canExport = false, hasContent = false, canPlay = false)
        )
    }

    @Test
    fun tanpaBenderaPutarTidakAdaSecaraBawaan() {
        assertFalse(CodeFileAction.PLAY_MPV in CodeFileActions.available(file, canExport = true, hasContent = true))
    }

    @Test
    fun tanpaBerkasTidakAdaAksiPutar() {
        assertTrue(CodeFileActions.available(null, canExport = true, hasContent = true, canPlay = true).isEmpty())
    }
}
