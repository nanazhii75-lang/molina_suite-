package com.molina.suite.feature.settings.search

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlTextTest {

    @Test
    fun stripsTagsAndDecodesEntities() {
        assertEquals("Tom & Jerry \"lucu\"", HtmlText.plain("Tom <b>&amp;</b> Jerry &quot;lucu&quot;"))
    }

    @Test
    fun decodesNumericEntities() {
        assertEquals("A'B", HtmlText.plain("A&#39;B"))
        assertEquals("\u00E9", HtmlText.plain("&#xE9;"))
    }

    @Test
    fun escapedTagsStayText() {
        assertEquals("<script>", HtmlText.plain("&lt;script&gt;"))
    }

    @Test
    fun dropsControlAndBidiCharacters() {
        assertEquals("abc", HtmlText.plain("a\u202Eb\u0007c"))
    }

    @Test
    fun collapsesWhitespace() {
        assertEquals("a b c", HtmlText.plain("  a \n\t b   c "))
    }

    @Test
    fun invalidNumericEntityIsDropped() {
        assertEquals("ab", HtmlText.plain("a&#0;b"))
        assertEquals("a&bogus;b", HtmlText.plain("a&bogus;b"))
    }
}
