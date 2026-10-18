package com.molina.suite.feature.settings.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuckDuckGoParserTest {

    private val page = """
        <html><body><div class="results">
        <div class="result results_links results_links_deep result--ad">
          <div class="links_main links_deep result__body">
            <h2 class="result__title"><a rel="nofollow" class="result__a" href="//duckduckgo.com/y.js?ad_domain=shop.example&amp;ad_provider=bingv7aa">Beli murah sekarang</a></h2>
            <a class="result__snippet" href="//duckduckgo.com/y.js?ad_domain=shop.example">Iklan berbayar</a>
          </div>
        </div>
        <div class="result results_links results_links_deep web-result ">
          <div class="links_main links_deep result__body">
            <h2 class="result__title"><a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.org%2Fhalaman%3Futm_source%3Dnews%26id%3D7&amp;rut=abc">Contoh &amp; <b>Halaman</b></a></h2>
            <div class="result__extras"><a class="result__url" href="//duckduckgo.com/l/?uddg=x">example.org/halaman</a></div>
            <a class="result__snippet" href="//duckduckgo.com/l/?uddg=x">Ini <b>cuplikan</b> &quot;uji&quot;.</a>
          </div>
        </div>
        <div class="result results_links results_links_deep web-result ">
          <div class="links_main links_deep result__body">
            <h2 class="result__title"><a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fads.doubleclick.net%2Fclk%2F1&amp;rut=1">Pelacak</a></h2>
            <a class="result__snippet" href="x">Tidak boleh tampil</a>
          </div>
        </div>
        <div class="result results_links results_links_deep web-result ">
          <div class="links_main links_deep result__body">
            <h2 class="result__title"><a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.example.org%2Fhalaman%3Fid%3D7%26utm_medium%3Dx&amp;rut=2">Contoh ganda</a></h2>
            <a class="result__snippet" href="x">Duplikat</a>
          </div>
        </div>
        <div class="result results_links results_links_deep web-result ">
          <div class="links_main links_deep result__body">
            <h2 class="result__title"><a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=http%3A%2F%2Fold.example.net%2F&amp;rut=3">Situs lama</a></h2>
            <div class="result__snippet">Tanpa HTTPS</div>
          </div>
        </div>
        <div class="result results_links results_links_deep web-result ">
          <div class="links_main links_deep result__body">
            <h2 class="result__title"><a rel="nofollow" class="result__a" href="javascript:alert(1)">Berbahaya</a></h2>
          </div>
        </div>
        </div></body></html>
    """.trimIndent()

    @Test
    fun keepsOnlyCleanOrganicResults() {
        val parsed = DuckDuckGoParser.parse(page)
        assertEquals(listOf("https://example.org/halaman?id=7", "http://old.example.net/"), parsed.results.map { it.url })
        assertTrue(parsed.sawResultMarkup)
    }

    @Test
    fun countsAdsAndTrackersAsBlocked() {
        // iklan, doubleclick, dan javascript: ; duplikat tidak dihitung
        assertEquals(3, DuckDuckGoParser.parse(page).blocked)
    }

    @Test
    fun extractsPlainTitleHostAndSnippet() {
        val first = DuckDuckGoParser.parse(page).results.first()
        assertEquals("Contoh & Halaman", first.title)
        assertEquals("example.org", first.host)
        assertEquals("Ini cuplikan \"uji\".", first.snippet)
        assertTrue(first.secure)
    }

    @Test
    fun marksPlainHttpAsInsecure() {
        val old = DuckDuckGoParser.parse(page).results.last()
        assertFalse(old.secure)
        assertEquals("Tanpa HTTPS", old.snippet)
    }

    @Test
    fun detectsChallengePage() {
        assertTrue(DuckDuckGoParser.isChallenge("<div class=\"anomaly-modal__title\">x</div>"))
        assertFalse(DuckDuckGoParser.isChallenge(page))
    }

    @Test
    fun emptyHtmlHasNoResults() {
        val parsed = DuckDuckGoParser.parse("<html></html>")
        assertTrue(parsed.results.isEmpty())
        assertFalse(parsed.sawResultMarkup)
    }
}
