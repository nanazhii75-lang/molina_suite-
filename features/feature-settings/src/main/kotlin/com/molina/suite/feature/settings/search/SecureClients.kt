package com.molina.suite.feature.settings.search

import com.molina.suite.core.common.ConnectionLedger
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.dnsoverhttps.DnsOverHttps

/** Pin kunci publik sertifikat DuckDuckGo. Perantara dan akar DigiCert dipin; sertifikat daun tidak. */
object DuckDuckGoPins {
    /** DigiCert Global G2 TLS RSA SHA256 2020 CA1 (diambil 2026-10-18 lewat alamat DuckDuckGo asli). */
    const val INTERMEDIATE = "sha256/Wec45nQiFwKvHtuHxSAMGkt19k+uPSw9JlEkxhvYPHk="

    /** DigiCert Global Root G2, cadangan bila perantara diganti oleh CA yang sama. */
    const val ROOT = "sha256/i7WTqTvh0OioIruIfFR4kMPnBqrS2rdiVPl/s2uC/CY="

    fun pinner(): CertificatePinner =
        CertificatePinner.Builder().add(DuckDuckGoSearch.HOST, INTERMEDIATE, ROOT).build()
}

/** DoH lewat alamat IP langsung, sehingga tidak butuh DNS sistem sama sekali. */
object SecureDns {
    val ENDPOINTS = listOf(
        "https://1.1.1.1/dns-query",
        "https://9.9.9.9/dns-query",
        "https://1.0.0.1/dns-query"
    )

    fun create(bootstrap: OkHttpClient, ledger: ConnectionLedger): HardenedDns {
        val entries = ENDPOINTS.map { endpoint ->
            val doh = DnsOverHttps.Builder()
                .client(bootstrap)
                .url(endpoint.toHttpUrl())
                .includeIPv6(false)
                .build()
            HardenedDns.Entry(endpoint, doh)
        }
        return HardenedDns(entries, ledger)
    }
}

/**
 * Pabrik klien HTTP yang dikeraskan: TLS 1.2+ dengan cipher terbatas, proxy sistem diabaikan (jaringan
 * bisa mendorong proxy sendiri), tanpa cookie, tanpa cache, tanpa pengalihan otomatis, tanpa percobaan
 * ulang diam-diam.
 */
object SecureClients {

    private fun base(proxy: Proxy): OkHttpClient.Builder = OkHttpClient.Builder()
        .proxy(proxy)
        .connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS))
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .cookieJar(CookieJar.NO_COOKIES)
        .cache(null)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)

    /** Klien untuk kueri DoH; waktu tunggu pendek agar cadangan cepat dicoba. */
    fun dohBootstrap(): OkHttpClient = base(Proxy.NO_PROXY)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    /** Klien untuk halaman tujuan: DNS aman, pin sertifikat, dan proxy hanya bila diminta pengguna. */
    fun pinned(dns: Dns, pinner: CertificatePinner, proxy: Proxy = Proxy.NO_PROXY): OkHttpClient =
        base(proxy)
            .dns(dns)
            .certificatePinner(pinner)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
}
