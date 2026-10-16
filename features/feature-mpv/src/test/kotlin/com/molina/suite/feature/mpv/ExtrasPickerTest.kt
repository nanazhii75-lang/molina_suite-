package com.molina.suite.feature.mpv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasPickerTest {

    private fun sub(
        lang: String,
        ext: String,
        auto: Boolean = false,
        url: String = "https://www.youtube.com/api/timedtext?v=1&lang=$lang"
    ) = RawSubtitle(lang, "Bahasa $lang", ext, url, auto)

    @Test
    fun babDiurutkanTanpaDuplikatDanDalamDurasi() {
        val result = ExtrasPicker.chapters(
            listOf(60 to "Dua", 0 to "Awal", 60 to "Duplikat", 999 to "Di luar", -5 to "Negatif"),
            durationSec = 300
        )
        assertEquals(listOf(ChapterItem(0, "Awal"), ChapterItem(60, "Dua")), result)
    }

    @Test
    fun satuBabDianggapTidakAda() {
        assertTrue(ExtrasPicker.chapters(listOf(0 to "Satu"), durationSec = 100).isEmpty())
    }

    @Test
    fun judulBabDibersihkanDariKarakterKontrol() {
        val result = ExtrasPicker.chapters(listOf(0 to " A\u0000B\n ", 10 to "C"), durationSec = 0)
        assertEquals("AB", result[0].title)
    }

    @Test
    fun ttmlTimedtextDimintaUlangSebagaiVtt() {
        val result = ExtrasPicker.subtitles(
            listOf(sub("id", "ttml", url = "https://www.youtube.com/api/timedtext?v=1&lang=id&fmt=ttml"))
        )
        assertEquals(1, result.size)
        assertEquals("vtt", result[0].ext)
        assertTrue(result[0].url.endsWith("&fmt=vtt"))
    }

    @Test
    fun ttmlTanpaPolaTimedtextDibuang() {
        assertTrue(ExtrasPicker.subtitles(listOf(sub("id", "ttml"))).isEmpty())
    }

    @Test
    fun manualDiAtasOtomatisDanOtomatisBerkunciAuto() {
        val result = ExtrasPicker.subtitles(listOf(sub("en", "vtt", auto = true), sub("id", "vtt")))
        assertEquals(listOf("id", "en-auto"), result.map { it.key })
        assertEquals("Bahasa en (otomatis)", result[1].name)
    }

    @Test
    fun vttDiutamakanDariSrtUntukBahasaSama() {
        val result = ExtrasPicker.subtitles(listOf(sub("id", "srt"), sub("id", "vtt")))
        assertEquals(1, result.size)
        assertEquals("vtt", result[0].ext)
    }

    @Test
    fun urlBukanHttpsDanBahasaAnehDibuang() {
        val result = ExtrasPicker.subtitles(
            listOf(
                sub("id", "vtt", url = "http://x/s.vtt"),
                sub("a b", "vtt"),
                sub("en", "vtt")
            )
        )
        assertEquals(listOf("en"), result.map { it.key })
    }

    @Test
    fun jumlahSubtitleDibatasiDuaBelas() {
        val many = (1..30).map { sub("l$it", "vtt") }
        assertEquals(12, ExtrasPicker.subtitles(many).size)
    }

    private val meta = VideoMeta("abc", "Judul", "https://www.youtube.com/watch?v=abc", 100, null, null)
    private val combined = MediaCandidate("18", "https://x/18", "mp4", "avc1", 360, 640, 30, 500, StreamClient.IOS)

    @Test
    fun jsonMemuatBabDanSubtitle() {
        val extras = MediaExtras(
            listOf(ChapterItem(0, "Awal"), ChapterItem(60, "Bagian \"2\"")),
            listOf(SubtitleItem("id", "vtt", "https://y/s?fmt=vtt", "Indonesia"))
        )
        val json = YtdlJson.forChoice(meta, StreamChoice(null, null, combined), { "UA" }, extras)
        assertTrue(json.contains("\"chapters\":[{\"start_time\":0,\"title\":\"Awal\"},{\"start_time\":60,\"title\":\"Bagian \\\"2\\\"\"}]"))
        assertTrue(json.contains("\"requested_subtitles\":{\"id\":{\"ext\":\"vtt\",\"url\":\"https://y/s?fmt=vtt\",\"name\":\"Indonesia\"}}"))
    }

    @Test
    fun jsonTanpaExtrasTidakMemuatBabDanSubtitle() {
        val json = YtdlJson.forChoice(meta, StreamChoice(null, null, combined), { "UA" })
        assertFalse(json.contains("chapters"))
        assertFalse(json.contains("requested_subtitles"))
    }
}
