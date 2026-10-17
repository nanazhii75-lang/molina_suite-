package com.molina.suite.feature.settings.connections

import com.molina.suite.core.common.ActivitySource
import com.molina.suite.core.common.ConnectionLedger
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionRowsTest {

    private fun entry(host: String, port: Int?, secure: Boolean, last: Long, source: ActivitySource = ActivitySource.MPV) =
        ConnectionLedger.Entry(host, port, secure, source, 1L, last)

    @Test
    fun tanpaEnkripsiDiDepanLaluYangTerbaru() {
        val rows = ConnectionRows.of(
            listOf(
                entry("a.id", null, true, 30),
                entry("b.id", null, true, 10),
                entry("c.id", 1935, false, 5)
            )
        )
        assertEquals(listOf("c.id:1935", "a.id", "b.id"), rows.map { it.hostLabel })
    }

    @Test
    fun portBawaanTidakDitampilkan() {
        val rows = ConnectionRows.of(listOf(entry("a.id", null, true, 1), entry("b.id", 8080, true, 2)))
        assertEquals(setOf("a.id", "b.id:8080"), rows.map { it.hostLabel }.toSet())
    }

    @Test
    fun ringkasanMenghitungHostBerbedaSaja() {
        val rows = ConnectionRows.of(
            listOf(
                entry("a.id", null, true, 1),
                entry("a.id", null, true, 2, ActivitySource.TERMINAL),
                entry("b.id", null, false, 3),
                entry("b.id", null, false, 4, ActivitySource.TERMINAL)
            )
        )
        val summary = ConnectionRows.summarize(rows)
        assertEquals(2, summary.hosts)
        assertEquals(1, summary.insecure)
    }

    @Test
    fun kosongMenghasilkanRingkasanNol() {
        val summary = ConnectionRows.summarize(ConnectionRows.of(emptyList()))
        assertEquals(0, summary.hosts)
        assertEquals(0, summary.insecure)
    }
}
