package com.molina.suite.feature.settings.search

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetPolicyTest {

    private val allowed = setOf("html.duckduckgo.com")

    @Test
    fun allowsOnlyHttpsDefaultPortOnAllowedHost() {
        assertTrue(TargetPolicy.allows("https://html.duckduckgo.com/html/".toHttpUrl(), allowed))
        assertTrue(TargetPolicy.allows("https://HTML.DuckDuckGo.com/html/?q=a".toHttpUrl(), allowed))
    }

    @Test
    fun rejectsPlainHttp() {
        assertFalse(TargetPolicy.allows("http://html.duckduckgo.com/html/".toHttpUrl(), allowed))
    }

    @Test
    fun rejectsOtherHostsAndLookalikes() {
        assertFalse(TargetPolicy.allows("https://aduankonten.id/".toHttpUrl(), allowed))
        assertFalse(TargetPolicy.allows("https://html.duckduckgo.com.evil.example/".toHttpUrl(), allowed))
        assertFalse(TargetPolicy.allows("https://duckduckgo.com/".toHttpUrl(), allowed))
    }

    @Test
    fun rejectsOddPortsAndUserInfo() {
        assertFalse(TargetPolicy.allows("https://html.duckduckgo.com:8443/html/".toHttpUrl(), allowed))
        assertFalse(TargetPolicy.allows("https://user:pw@html.duckduckgo.com/html/".toHttpUrl(), allowed))
        assertFalse(TargetPolicy.allows("https://user@html.duckduckgo.com/html/".toHttpUrl(), allowed))
    }
}
