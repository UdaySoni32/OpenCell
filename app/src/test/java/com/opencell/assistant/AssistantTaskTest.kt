package com.opencell.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantTaskTest {
    @Test
    fun `relative reminder parses from a natural command`() {
        val draft = AssistantTaskCommandParser.reminder("Remind me to call the clinic in 15 minutes", now = 1_000L)!!
        assertEquals("call the clinic", draft.title)
        assertEquals(901_000L, draft.triggerAtMillis)
    }

    @Test
    fun `tomorrow reminder uses local timezone`() {
        val now = java.time.LocalDateTime.of(2026, 10, 17, 12, 0)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val draft = AssistantTaskCommandParser.reminder("remind me to call mom tomorrow at 9am", now)!!
        val expected = java.time.LocalDateTime.of(2026, 10, 18, 9, 0)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, draft.triggerAtMillis)
    }

    @Test
    fun `task and completion commands are bounded`() {
        assertEquals("send the invoice", AssistantTaskCommandParser.task("todo: send the invoice")!!.title)
        assertEquals("send the invoice", AssistantTaskCommandParser.completeTask("complete task: send the invoice"))
        assertTrue(AssistantTaskCommandParser.task("task") == null)
    }
}
