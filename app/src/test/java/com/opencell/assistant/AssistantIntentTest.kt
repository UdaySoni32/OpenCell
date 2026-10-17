package com.opencell.assistant

import com.opencell.memory.MemoryCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantIntentTest {
    @Test
    fun `parses an answer intent`() {
        val intent = AssistantIntentParser.parse("""{"type":"answer","message":"I can help with that."}""")
        assertEquals(AssistantIntent.Answer("I can help with that."), intent)
    }

    @Test
    fun `parses SMS draft but does not execute it`() {
        val intent = AssistantIntentParser.parse(
            """{"type":"send_sms","to":"+919876543210","body":"Running late","reason":"User asked"}""",
        ) as AssistantIntent.SendSms
        assertEquals("+919876543210", intent.to)
        assertEquals("Running late", intent.body)
    }

    @Test
    fun `rejects malformed or unsafe action output`() {
        assertNull(AssistantIntentParser.parse("not json"))
        assertNull(AssistantIntentParser.parse("""{"type":"send_sms","to":"not a number","body":"hi"}"""))
        assertNull(AssistantIntentParser.parse("""{"type":"unknown","message":"x"}"""))
    }

    @Test
    fun `remember intent defaults unknown categories safely`() {
        val intent = AssistantIntentParser.parse("""{"type":"remember","text":"Likes tea","category":"unknown"}""")
        assertTrue(intent is AssistantIntent.Remember)
        assertEquals(MemoryCategory.GENERAL, (intent as AssistantIntent.Remember).category)
    }
}
