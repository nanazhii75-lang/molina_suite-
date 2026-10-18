package com.molina.suite.feature.settings.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultFilterTest {

    private fun result(url: String) = SearchResult("t", url, "h", "s")

    private val mixed = listOf(
        result("https://a.example/"),
        result("http://b.example/"),
        result("HTTPS://c.example/"),
        result("http://d.example/")
    )

    @Test
    fun httpsOnlyDropsPlainHttpAndCountsIt() {
        val out = SearchResultFilter.apply(mixed, httpsOnly = true)
        assertEquals(listOf("https://a.example/", "HTTPS://c.example/"), out.kept.map { it.url })
        assertEquals(2, out.droppedHttp)
    }

    @Test
    fun disabledFilterKeepsEverythingAndDropsNothing() {
        val out = SearchResultFilter.apply(mixed, httpsOnly = false)
        assertEquals(4, out.kept.size)
        assertEquals(0, out.droppedHttp)
    }

    @Test
    fun emptyInputGivesEmptyOutput() {
        val out = SearchResultFilter.apply(emptyList(), httpsOnly = true)
        assertTrue(out.kept.isEmpty())
        assertEquals(0, out.droppedHttp)
    }
}
