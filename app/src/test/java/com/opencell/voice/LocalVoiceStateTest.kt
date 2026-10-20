package com.opencell.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalVoiceStateTest {
    @Test
    fun `only active states allow a second session to be rejected`() {
        assertTrue(LocalVoiceStatus.STARTING.active)
        assertTrue(LocalVoiceStatus.LISTENING.active)
        assertTrue(LocalVoiceStatus.SPEAKING.active)
        assertFalse(LocalVoiceStatus.IDLE.active)
        assertFalse(LocalVoiceStatus.ERROR.active)
    }

    @Test
    fun `transcript preserves speaker and text`() {
        val state = LocalVoiceState(lines = listOf(TranscriptLine(Speaker.THEM, "Hello")))

        assertEquals(Speaker.THEM, state.lines.single().speaker)
        assertEquals("Hello", state.lines.single().text)
    }
}
