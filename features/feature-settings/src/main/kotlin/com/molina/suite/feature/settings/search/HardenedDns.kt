package com.molina.suite.feature.settings.search

import com.molina.suite.core.common.ActivitySource
import com.molina.suite.core.common.ConnectionLedger
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns

/**
 * Resolver DNS-over-HTTPS berantai. DNS jaringan tidak pernah dipakai: ISP dan jaringan publik bisa
 * membajaknya (terbukti mengarahkan DuckDuckGo ke halaman blokir). Bila semua penyedia gagal, lookup
 * gagal tertutup dan tidak jatuh ke DNS sistem. Jawaban ke alamat lokal/privat dibuang. Tiap penyedia
 * yang dihubungi tercatat di Catatan Koneksi.
 */
class HardenedDns(
    private val resolvers: List<Entry>,
    private val ledger: ConnectionLedger = ConnectionLedger.shared
) : Dns {

    class Entry(val endpoint: String, val dns: Dns)

    override fun lookup(hostname: String): List<InetAddress> {
        for (entry in resolvers) {
            ledger.record(ActivitySource.SETTING, entry.endpoint)
            val answers = try {
                entry.dns.lookup(hostname)
            } catch (e: Exception) {
                continue
            }
            val safe = answers.filter { PublicAddress.isPublic(it) }
            if (safe.isNotEmpty()) return safe
        }
        throw UnknownHostException("DNS aman gagal pada semua penyedia")
    }
}
