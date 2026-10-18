package com.molina.suite.feature.settings.search

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicAddressTest {

    private fun ip(vararg octets: Int): InetAddress =
        InetAddress.getByAddress(ByteArray(octets.size) { octets[it].toByte() })

    @Test
    fun ordinaryPublicAddressesAreAllowed() {
        assertTrue(PublicAddress.isPublic(ip(20, 43, 161, 105)))
        assertTrue(PublicAddress.isPublic(ip(1, 1, 1, 1)))
        assertTrue(PublicAddress.isPublic(ip(0x26, 0x06, 0x47, 0x00, 0x30, 0x30, 0, 0, 0, 0, 0, 0, 0xac, 0x43, 0xbe, 0x3c)))
    }

    @Test
    fun localAndPrivateIpv4AreRejected() {
        val rejected = listOf(
            ip(0, 0, 0, 0), ip(127, 0, 0, 1), ip(10, 0, 0, 1), ip(172, 16, 0, 1), ip(192, 168, 1, 1),
            ip(169, 254, 1, 1), ip(100, 64, 0, 1), ip(100, 127, 255, 255), ip(224, 0, 0, 1),
            ip(198, 18, 0, 1), ip(240, 0, 0, 1), ip(255, 255, 255, 255)
        )
        rejected.forEach { assertFalse(it.hostAddress, PublicAddress.isPublic(it)) }
    }

    @Test
    fun edgesOfReservedRangesStayPublic() {
        assertTrue(PublicAddress.isPublic(ip(100, 63, 255, 255)))
        assertTrue(PublicAddress.isPublic(ip(100, 128, 0, 1)))
        assertTrue(PublicAddress.isPublic(ip(198, 20, 0, 1)))
    }

    @Test
    fun localIpv6AreRejected() {
        val loopback = ip(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)
        val linkLocal = ip(0xfe, 0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)
        val uniqueLocalC = ip(0xfc, 0x00, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)
        val uniqueLocalD = ip(0xfd, 0x12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)
        listOf(loopback, linkLocal, uniqueLocalC, uniqueLocalD).forEach {
            assertFalse(it.hostAddress, PublicAddress.isPublic(it))
        }
    }

    @Test
    fun ipv4MappedPrivateAddressIsRejected() {
        val mapped = ip(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xff, 0xff, 10, 0, 0, 1)
        assertEquals(4, mapped.address.size)
        assertFalse(PublicAddress.isPublic(mapped))
    }
}
