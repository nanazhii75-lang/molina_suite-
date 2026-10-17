package com.molina.suite.feature.mpv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewPipeBridgeLogicTest {

    private fun candidate(
        id: String,
        ext: String,
        codec: String?,
        height: Int,
        bitrate: Int,
        client: StreamClient = StreamClient.IOS
    ) = MediaCandidate(id, "https://x/$id", ext, codec, height, height * 16 / 9, 30, bitrate, client)

    private val videos = listOf(
        candidate("v1", "mp4", "avc1.640028", 1080, 4000),
        candidate("v2", "webm", "vp9", 1080, 3000),
        candidate("v3", "mp4", "av01.0.08M.08", 1080, 2000),
        candidate("v4", "mp4", "avc1.4d401f", 720, 1500),
        candidate("v5", "webm", "vp9", 2160, 9000)
    )
    private val audios = listOf(
        candidate("a1", "m4a", "mp4a.40.2", 0, 128),
        candidate("a2", "webm", "opus", 0, 160),
        candidate("a3", "m4a", "mp4a.40.2", 0, 256, StreamClient.WEB)
    )

    @Test
    fun batasTinggiDiambilDariSelector() {
        assertEquals(720, StreamPicker.maxHeight("bestvideo[height<=?720][vcodec!*=?av01]+bestaudio/best"))
        assertEquals(2160, StreamPicker.maxHeight("bestvideo[height<=2160]"))
        assertEquals(1080, StreamPicker.maxHeight("best"))
        assertEquals(1080, StreamPicker.maxHeight(null))
    }

    @Test
    fun memilihH264DiBatasTanpaAv1DanAudioKlienSama() {
        val choice = StreamPicker.pick(videos, audios, emptyList(), 1080)!!
        assertEquals("v1", choice.video!!.formatId)
        assertEquals("a1", choice.audio!!.formatId)
    }

    @Test
    fun mengikutiBatasTinggi() {
        assertEquals("v4", StreamPicker.pick(videos, audios, emptyList(), 720)!!.video!!.formatId)
        assertEquals("v5", StreamPicker.pick(listOf(videos[4]), audios, emptyList(), 480)!!.video!!.formatId)
    }

    @Test
    fun jatuhKeStreamGabunganBilaTidakAdaVideoSaja() {
        val progressive = listOf(candidate("p1", "mp4", "avc1.42001e", 360, 500))
        assertEquals("p1", StreamPicker.pick(emptyList(), emptyList(), progressive, 1080)!!.combined!!.formatId)
        assertEquals("p1", StreamPicker.pick(videos, emptyList(), progressive, 1080)!!.combined!!.formatId)
        assertNull(StreamPicker.pick(emptyList(), emptyList(), emptyList(), 1080))
    }

    @Test
    fun jsonSatuBarisDenganEscapeDanHeaderPerKlien() {
        val meta = VideoMeta("abc", "Judul \"x\"\\\n", "https://www.youtube.com/watch?v=abc", 123, "Pengunggah", null)
        val choice = StreamPicker.pick(videos, audios, emptyList(), 1080)!!
        val json = YtdlJson.forChoice(meta, choice) { "UA-" + it }
        assertFalse(json.contains('\n'))
        assertTrue(json.contains("\"requested_formats\":[{"))
        assertTrue(json.contains("\"User-Agent\":\"UA-IOS\""))
        assertTrue(json.contains("Judul \\\"x\\\"\\\\\\n"))
    }

    @Test
    fun jsonGabunganDanSiaranLangsungMemakaiUrlTingkatAtas() {
        val meta = VideoMeta("abc", "t", "https://www.youtube.com/watch?v=abc", 0, null, null)
        val muxed = candidate("p1", "mp4", "avc1.42001e", 360, 500)
        val combined = YtdlJson.forChoice(meta, StreamChoice(null, null, muxed)) { "UA" }
        assertTrue(combined.contains("\"url\":\"https://x/p1\""))
        assertFalse(combined.contains("requested_formats"))
        val live = YtdlJson.forLive(meta, "https://m/h.m3u8", "UA")
        assertTrue(live.contains("\"is_live\":true"))
        assertTrue(live.contains("\"url\":\"https://m/h.m3u8\""))
    }

    @Test
    fun hanyaUrlYoutubeHttpsYangDikenali() {
        val id = "dQw4w9WgXcQ"
        assertEquals(id, YoutubeWatchUrl.videoId("https://www.youtube.com/watch?v=$id&list=PL1&t=3"))
        assertEquals(id, YoutubeWatchUrl.videoId("https://m.youtube.com/watch?feature=share&v=$id"))
        assertEquals(id, YoutubeWatchUrl.videoId("https://youtu.be/$id?si=abc"))
        assertEquals(id, YoutubeWatchUrl.videoId("https://www.youtube.com/shorts/$id"))
        assertNull(YoutubeWatchUrl.videoId("http://www.youtube.com/watch?v=$id"))
        assertNull(YoutubeWatchUrl.videoId("https://youtube.com.evil.com/watch?v=$id"))
        assertNull(YoutubeWatchUrl.videoId("https://www.youtube.com/watch?v=${id}x"))
        assertNull(YoutubeWatchUrl.videoId("https://www.youtube.com/playlist?list=PL123"))
        assertNull(YoutubeWatchUrl.videoId("ytsearch10:kucing"))
        assertNull(YoutubeWatchUrl.videoId("https://vt.tiktok.com/ZS9UAj58sCH2F-LRl0p/"))
    }

    @Test
    fun selectorBawaanMendahulukanH264DanTetapMembacaBatasTinggi() {
        val f = MpvYtdlBridge.YTDL_FORMAT
        assertEquals(1080, StreamPicker.maxHeight(f))
        assertTrue(f.startsWith("bestvideo[height<=?1080]"))
        assertTrue(f.indexOf("[vcodec^=avc]") in 1 until f.indexOf("[vcodec!*=?av01]/best"))
        assertTrue(f.contains("[vcodec^=h264]"))
        assertTrue(f.endsWith("/best"))
        assertFalse(f.contains("'"))
    }
}
