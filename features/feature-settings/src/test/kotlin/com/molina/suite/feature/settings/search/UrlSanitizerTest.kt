package com.molina.suite.feature.settings.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlSanitizerTest {

    @Test
    fun rejectsNonWebSchemes() {
        assertNull(UrlSanitizer.clean("javascript:alert(1)"))
        assertNull(UrlSanitizer.clean("intent://scan/#Intent;scheme=zxing;end"))
        assertNull(UrlSanitizer.clean("file:///sdcard/Molina/a.txt"))
        assertNull(UrlSanitizer.clean("data:text/html,<b>x</b>"))
    }

    @Test
    fun rejectsDisguisedAndMalformedAuthority() {
        assertNull(UrlSanitizer.clean("https://bank.example.com@evil.example/login"))
        assertNull(UrlSanitizer.clean("https://example.com:80a/"))
        assertNull(UrlSanitizer.clean("https:///path"))
        assertNull(UrlSanitizer.clean("https://[::1]/"))
    }

    @Test
    fun stripsTrackingParametersAndKeepsTheRest() {
        assertEquals(
            "https://example.org/page?id=7&lang=id",
            UrlSanitizer.clean("https://Example.org/page?utm_source=a&id=7&fbclid=zzz&lang=id&gclid=1")
        )
        assertEquals("https://example.org/page", UrlSanitizer.clean("https://example.org/page?utm_medium=x&msclkid=2"))
    }

    @Test
    fun keepsFragmentAndPort() {
        assertEquals(
            "https://example.org:8443/a#bagian",
            UrlSanitizer.clean("https://example.org:8443/a?utm_term=t#bagian")
        )
    }

    @Test
    fun stripsVideoShareIdOnlyForVideoHosts() {
        assertEquals("https://www.youtube.com/watch?v=abc", UrlSanitizer.clean("https://www.youtube.com/watch?v=abc&si=XYZ"))
        assertEquals("https://example.org/?si=1", UrlSanitizer.clean("https://example.org/?si=1"))
    }

    @Test
    fun unwrapsDuckDuckGoRedirect() {
        val wrapped = "//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.org%2Fa%3Futm_source%3Dx%26k%3D1&rut=abc"
        assertEquals("https://example.org/a?k=1", UrlSanitizer.clean(wrapped))
    }

    @Test
    fun unwrapsGoogleFacebookAndYoutubeRedirects() {
        assertEquals("https://example.org/x", UrlSanitizer.clean("https://www.google.com/url?q=https%3A%2F%2Fexample.org%2Fx&sa=U"))
        assertEquals("https://example.org/x", UrlSanitizer.clean("https://www.google.co.id/url?url=https%3A%2F%2Fexample.org%2Fx"))
        assertEquals("https://example.org/", UrlSanitizer.clean("https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.org%2F&h=AT0"))
        assertEquals("https://example.org/", UrlSanitizer.clean("https://www.youtube.com/redirect?q=https%3A%2F%2Fexample.org%2F&event=video"))
    }

    @Test
    fun rejectsRedirectWhoseTargetIsUnsafe() {
        assertNull(UrlSanitizer.clean("//duckduckgo.com/l/?uddg=javascript%3Aalert(1)"))
        assertNull(UrlSanitizer.clean("https://www.google.com/url?q=intent%3A%2F%2Fx"))
    }

    @Test
    fun rejectsAdAndTrackerHosts() {
        assertNull(UrlSanitizer.clean("https://ad.doubleclick.net/ddm/clk/1"))
        assertNull(UrlSanitizer.clean("https://googleadservices.com/pagead/aclk?x=1"))
        assertNull(UrlSanitizer.clean("https://www.bing.com/aclick?ld=abc"))
        assertNull(UrlSanitizer.clean("//duckduckgo.com/y.js?ad_domain=shop.example"))
        assertEquals("https://notdoubleclick.net/", UrlSanitizer.clean("https://notdoubleclick.net/"))
    }

    @Test
    fun displayHostDropsWww() {
        assertEquals("example.org", UrlSanitizer.displayHost("https://www.example.org/a"))
    }

    @Test
    fun dedupeKeyIgnoresWwwAndTrailingSlash() {
        assertEquals(
            UrlSanitizer.dedupeKey("https://www.example.org/a/"),
            UrlSanitizer.dedupeKey("https://example.org/a")
        )
    }
}
