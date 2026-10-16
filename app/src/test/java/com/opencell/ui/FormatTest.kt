package com.opencell.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `formatDuration formats correctly`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:09", formatDuration(9))
        assertEquals("0:59", formatDuration(59))
        assertEquals("1:00", formatDuration(60))
        assertEquals("1:09", formatDuration(69))
        assertEquals("10:05", formatDuration(605))
        assertEquals("59:59", formatDuration(3599))
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("1:00:09", formatDuration(3609))
        assertEquals("1:01:01", formatDuration(3661))
        assertEquals("10:00:00", formatDuration(36000))
    }
}
