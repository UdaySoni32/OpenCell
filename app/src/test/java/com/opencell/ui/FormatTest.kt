package com.opencell.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `initialOf returns first letter uppercase for valid names`() {
        assertEquals("A", initialOf("Alice"))
        assertEquals("B", initialOf("bob"))
        assertEquals("C", initialOf("Charlie"))
        assertEquals("Z", initialOf("z"))
    }

    @Test
    fun `initialOf returns first letter uppercase ignoring non-letters`() {
        assertEquals("B", initialOf("123 Bob"))
        assertEquals("C", initialOf("+1 (234) Charlie"))
        assertEquals("D", initialOf("!@# Dan"))
    }

    @Test
    fun `initialOf returns hash for strings without letters`() {
        assertEquals("#", initialOf("12345"))
        assertEquals("#", initialOf("+1234567890"))
        assertEquals("#", initialOf("!@#$%"))
        assertEquals("#", initialOf("   "))
        assertEquals("#", initialOf(""))
    }
}
