package com.molina.suite.feature.settings.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueryTextTest {

    @Test
    fun blankIsRejected() {
        assertNull(QueryText.normalize("   \n\t"))
        assertNull(QueryText.normalize("\u0000\u0007"))
    }

    @Test
    fun whitespaceIsCollapsed() {
        assertEquals("kucing lucu", QueryText.normalize("  kucing \n  lucu "))
    }

    @Test
    fun lengthIsLimited() {
        val result = QueryText.normalize("a".repeat(1000))
        assertEquals(QueryText.MAX_LENGTH, result?.length)
    }
}
