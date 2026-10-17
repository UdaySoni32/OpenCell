package com.opencell.assistant

import com.opencell.ai.AiSettings
import com.opencell.ai.ChatMessage
import com.opencell.ai.LlmException
import com.opencell.ai.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantChatPromptTest {
    @Test
    fun `prompt includes user instructions and preserves chat roles`() {
        val prompt = buildAssistantPrompt(
            AiSettings(userName = "Uday"),
            "Reply briefly and use metric units.",
            listOf(ChatMessage(Role.USER, "Hello"), ChatMessage(Role.ASSISTANT, "Hi there")),
        )

        assertTrue(prompt.system.contains("Uday's instructions:\nReply briefly and use metric units."))
        assertTrue(prompt.system.contains("Do not send SMS, place calls"))
        assertEquals(listOf(Role.USER, Role.ASSISTANT), prompt.messages.map { it.role })
        assertEquals(listOf("Hello", "Hi there"), prompt.messages.map { it.content })
    }

    @Test
    fun `prompt keeps only the most recent history`() {
        val history = (1..41).map { ChatMessage(Role.USER, "message $it") }

        val prompt = buildAssistantPrompt(AiSettings(), "", history)

        assertEquals(40, prompt.messages.size)
        assertEquals("message 2", prompt.messages.first().content)
        assertEquals("message 41", prompt.messages.last().content)
    }

    @Test
    fun `prompt does not start with an assistant turn`() {
        val prompt = buildAssistantPrompt(
            AiSettings(),
            "",
            listOf(ChatMessage(Role.ASSISTANT, "old answer"), ChatMessage(Role.USER, "new question")),
        )

        assertEquals(listOf(Role.USER), prompt.messages.map { it.role })
    }

    @Test
    fun `provider failures become visible assistant errors`() {
        assertTrue(NO_PROVIDER_MESSAGE.startsWith("No AI provider configured"))
        assertEquals("Assistant error: connection refused", assistantErrorMessage(LlmException("connection refused")))
    }
}
