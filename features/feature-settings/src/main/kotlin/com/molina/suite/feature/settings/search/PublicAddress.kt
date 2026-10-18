package com.molina.suite.feature.settings.search

import java.net.InetAddress

/**
 * Menentukan apakah alamat hasil DNS boleh dihubungi. Jawaban DNS yang dibajak sering menunjuk ke
 * alamat lokal atau privat (pembelokan ke jaringan sendiri), jadi semuanya ditolak. Murni, tanpa I/O.
 */
object PublicAddress {

    fun isPublic(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) return false
        val bytes = address.address
        return when (bytes.size) {
            4 -> !reservedV4(bytes)
            16 -> !reservedV6(bytes)
            else -> false
        }
    }

    private fun octet(value: Byte): Int = value.toInt() and 0xFF

    private fun reservedV4(b: ByteArray): Boolean {
        val first = octet(b[0])
        val second = octet(b[1])
        return first == 0 ||
            (first == 100 && second in 64..127) ||
            (first == 192 && second == 0 && octet(b[2]) == 0) ||
            (first == 198 && second in 18..19) ||
            first >= 240
    }

    private fun reservedV6(b: ByteArray): Boolean = (octet(b[0]) and 0xFE) == 0xFC
}
