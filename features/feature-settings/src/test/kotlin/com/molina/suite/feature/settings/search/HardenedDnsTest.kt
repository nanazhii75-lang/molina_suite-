package com.molina.suite.feature.settings.search

import com.molina.suite.core.common.ConnectionLedger
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HardenedDnsTest {

    private fun ip(vararg octets: Int): InetAddress =
        InetAddress.getByAddress(ByteArray(octets.size) { octets[it].toByte() })

    private class FakeDns(private val answer: () -> List<InetAddress>) : Dns {
        var calls = 0
        override fun lookup(hostname: String): List<InetAddress> {
            calls++
            return answer()
        }
    }

    private val good = ip(20, 43, 161, 105)

    @Test
    fun firstWorkingResolverWinsAndLaterOnesAreNotAsked() {
        val first = FakeDns { listOf(good) }
        val second = FakeDns { listOf(ip(8, 8, 8, 8)) }
        val dns = HardenedDns(
            listOf(HardenedDns.Entry("https://1.1.1.1/dns-query", first), HardenedDns.Entry("https://9.9.9.9/dns-query", second)),
            ConnectionLedger()
        )
        assertEquals(listOf(good), dns.lookup("html.duckduckgo.com"))
        assertEquals(0, second.calls)
    }

    @Test
    fun failingResolverFallsBackToNextOne() {
        val first = FakeDns { throw UnknownHostException("gagal") }
        val second = FakeDns { listOf(good) }
        val dns = HardenedDns(
            listOf(HardenedDns.Entry("https://1.1.1.1/dns-query", first), HardenedDns.Entry("https://9.9.9.9/dns-query", second)),
            ConnectionLedger()
        )
        assertEquals(listOf(good), dns.lookup("html.duckduckgo.com"))
        assertEquals(1, first.calls)
    }

    @Test
    fun privateAnswersAreDroppedAndTriggerFallback() {
        val hijacked = FakeDns { listOf(ip(192, 168, 0, 1), ip(127, 0, 0, 1)) }
        val honest = FakeDns { listOf(good) }
        val dns = HardenedDns(
            listOf(HardenedDns.Entry("https://1.1.1.1/dns-query", hijacked), HardenedDns.Entry("https://9.9.9.9/dns-query", honest)),
            ConnectionLedger()
        )
        assertEquals(listOf(good), dns.lookup("html.duckduckgo.com"))
    }

    @Test
    fun mixedAnswerKeepsOnlyPublicAddresses() {
        val mixed = FakeDns { listOf(ip(10, 0, 0, 5), good) }
        val dns = HardenedDns(listOf(HardenedDns.Entry("https://1.1.1.1/dns-query", mixed)), ConnectionLedger())
        assertEquals(listOf(good), dns.lookup("html.duckduckgo.com"))
    }

    @Test
    fun allResolversFailingFailsClosed() {
        val broken = FakeDns { throw UnknownHostException("gagal") }
        val dns = HardenedDns(
            listOf(HardenedDns.Entry("https://1.1.1.1/dns-query", broken), HardenedDns.Entry("https://9.9.9.9/dns-query", broken)),
            ConnectionLedger()
        )
        try {
            dns.lookup("html.duckduckgo.com")
            fail("harus gagal tertutup")
        } catch (e: UnknownHostException) {
            assertEquals(2, broken.calls)
        }
    }

    @Test
    fun everyContactedResolverIsRecordedWithoutPathOrQuery() {
        val ledger = ConnectionLedger()
        val broken = FakeDns { throw UnknownHostException("gagal") }
        val ok = FakeDns { listOf(good) }
        val dns = HardenedDns(
            listOf(HardenedDns.Entry("https://1.1.1.1/dns-query", broken), HardenedDns.Entry("https://9.9.9.9/dns-query", ok)),
            ledger
        )
        dns.lookup("html.duckduckgo.com")
        val hosts = ledger.snapshot().map { it.host }.toSet()
        assertEquals(setOf("1.1.1.1", "9.9.9.9"), hosts)
        assertTrue(ledger.snapshot().all { it.secure })
    }
}
