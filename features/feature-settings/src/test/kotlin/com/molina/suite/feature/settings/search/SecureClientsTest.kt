package com.molina.suite.feature.settings.search

import java.net.Proxy
import java.util.Base64
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.CookieJar
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Penjaga kebijakan: kalau ada yang melemahkan pengaturan klien, tes ini gagal. */
class SecureClientsTest {

    private val client = SecureClients.pinned(Dns.SYSTEM, DuckDuckGoPins.pinner())

    @Test
    fun usesRestrictedTlsOnly() {
        assertEquals(listOf(ConnectionSpec.RESTRICTED_TLS), client.connectionSpecs)
    }

    @Test
    fun ignoresSystemProxyUnlessGivenExplicitly() {
        assertEquals(Proxy.NO_PROXY, client.proxy)
    }

    @Test
    fun keepsNoStateBetweenRequests() {
        assertSame(CookieJar.NO_COOKIES, client.cookieJar)
        assertNull(client.cache)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertFalse(client.retryOnConnectionFailure)
    }

    @Test
    fun pinnerIsNotTheEmptyDefault() {
        assertNotEquals(CertificatePinner.DEFAULT, client.certificatePinner)
    }

    @Test
    fun pinsAreWellFormedSha256Hashes() {
        for (pin in listOf(DuckDuckGoPins.INTERMEDIATE, DuckDuckGoPins.ROOT)) {
            assertTrue(pin.startsWith("sha256/"))
            assertEquals(32, Base64.getDecoder().decode(pin.removePrefix("sha256/")).size)
        }
    }

    @Test
    fun dohEndpointsAreHttpsIpLiteralsSoNoSystemDnsIsNeeded() {
        for (endpoint in SecureDns.ENDPOINTS) {
            assertTrue(endpoint.matches(Regex("^https://\\d{1,3}(\\.\\d{1,3}){3}/dns-query$")))
        }
    }
}
