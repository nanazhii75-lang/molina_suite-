package com.molina.suite.feature.mpv

import com.molina.suite.core.common.ActivitySource
import com.molina.suite.core.common.ConnectionLedger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionLedgerTest {

    private var now = 0L
    private fun ledger(capacity: Int = 200) = ConnectionLedger(capacity) { now }

    @Test
    fun hanyaHostYangDisimpan() {
        val ledger = ledger()
        ledger.record(ActivitySource.MPV, "https://Example.com/watch?v=abc123&token=rahasia")
        val entry = ledger.snapshot().single()
        assertEquals("example.com", entry.host)
        assertNull(entry.port)
        assertTrue(entry.secure)
        assertFalse(entry.toString().contains("abc123"))
        assertFalse(entry.toString().contains("rahasia"))
    }

    @Test
    fun httpDitandaiTidakAmanDanPortDibaca() {
        val ledger = ledger()
        ledger.record(ActivitySource.MPV, "http://122.248.43.242:1935/live/a.m3u8")
        val entry = ledger.snapshot().single()
        assertEquals("122.248.43.242", entry.host)
        assertEquals(1935, entry.port)
        assertFalse(entry.secure)
    }

    @Test
    fun userPasswordDibuang() {
        val ledger = ledger()
        ledger.record(ActivitySource.MPV, "https://budi:sandi@cdn.contoh.id/x")
        val entry = ledger.snapshot().single()
        assertEquals("cdn.contoh.id", entry.host)
        assertFalse(entry.toString().contains("sandi"))
        assertFalse(entry.toString().contains("budi"))
    }

    @Test
    fun ipv6DenganPort() {
        val ledger = ledger()
        ledger.record(ActivitySource.MPV, "http://[2001:db8::1]:8080/s")
        val entry = ledger.snapshot().single()
        assertEquals("[2001:db8::1]", entry.host)
        assertEquals(8080, entry.port)
    }

    @Test
    fun skemaLainDanSampahDiabaikan() {
        val ledger = ledger()
        listOf("rtmp://a.b/c", "file:///sdcard/x.mp4", "bukan url", "", "https:///tanpa-host", "http://a.b:xx/")
            .forEach { ledger.record(ActivitySource.MPV, it) }
        assertTrue(ledger.snapshot().isEmpty())
    }

    @Test
    fun hitunganBertambahDanWaktuTerakhirDiperbarui() {
        val ledger = ledger()
        now = 10
        ledger.record(ActivitySource.MPV, "https://a.id/1")
        now = 20
        ledger.record(ActivitySource.MPV, "https://a.id/2?x=1")
        val entry = ledger.snapshot().single()
        assertEquals(2L, entry.count)
        assertEquals(20L, entry.lastAtMillis)
    }

    @Test
    fun sumberBerbedaDipisah() {
        val ledger = ledger()
        ledger.record(ActivitySource.MPV, "https://a.id/")
        ledger.record(ActivitySource.TERMINAL, "https://a.id/")
        assertEquals(2, ledger.snapshot().size)
    }

    @Test
    fun kapasitasDibatasiDanYangTerlamaDibuang() {
        val ledger = ledger(capacity = 2)
        now = 1
        ledger.record(ActivitySource.MPV, "https://a.id/")
        now = 2
        ledger.record(ActivitySource.MPV, "https://b.id/")
        now = 3
        ledger.record(ActivitySource.MPV, "https://c.id/")
        assertEquals(listOf("c.id", "b.id"), ledger.snapshot().map { it.host })
    }

    @Test
    fun clearMengosongkan() {
        val ledger = ledger()
        ledger.record(ActivitySource.MPV, "https://a.id/")
        ledger.clear()
        assertTrue(ledger.snapshot().isEmpty())
    }
}
