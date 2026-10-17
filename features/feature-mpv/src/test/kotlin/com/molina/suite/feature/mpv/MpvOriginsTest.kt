package com.molina.suite.feature.mpv

import com.molina.suite.mpv.MpvOrigins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvOriginsTest {

    @Test
    fun jalurDanQueryDibuang() {
        assertEquals(
            listOf("https://a.id"),
            MpvOrigins.extract("https://a.id/watch?v=abc&token=rahasia#t=3")
        )
    }

    @Test
    fun userPasswordDibuangDanPortDipertahankan() {
        assertEquals(
            listOf("http://cdn.id:1935"),
            MpvOrigins.extract("http://budi:sandi@cdn.id:1935/live/a.m3u8")
        )
    }

    @Test
    fun alamatGabunganEdlMemberiBeberapaAsal() {
        val edl = "edl://!no_clip;!new_stream;%40%https://v.cdn.id/a.mp4?x=1;%40%https://a.cdn.id/b.m4a"
        assertEquals(listOf("https://v.cdn.id", "https://a.cdn.id"), MpvOrigins.extract(edl))
    }

    @Test
    fun duplikatDigabung() {
        assertEquals(
            listOf("https://a.id"),
            MpvOrigins.extract("https://a.id/1;https://a.id/2")
        )
    }

    @Test
    fun selainHttpDiabaikan() {
        assertTrue(MpvOrigins.extract(null).isEmpty())
        assertTrue(MpvOrigins.extract("").isEmpty())
        assertTrue(MpvOrigins.extract("/storage/emulated/0/Molina/Playlists/a.m3u").isEmpty())
        assertTrue(MpvOrigins.extract("rtmp://a.id/live").isEmpty())
    }

    @Test
    fun batasJumlahPerTeks() {
        val text = (1..12).joinToString(";") { "https://h$it.id/x" }
        assertEquals(MpvOrigins.MAX_PER_TEXT, MpvOrigins.extract(text).size)
    }

    @Test
    fun ipv6DenganPort() {
        assertEquals(
            listOf("http://[2001:db8::1]:8080"),
            MpvOrigins.extract("http://[2001:db8::1]:8080/x")
        )
    }
}
