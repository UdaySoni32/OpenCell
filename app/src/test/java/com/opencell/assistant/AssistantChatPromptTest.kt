package com.opencell.assistant

import com.opencell.ai.ChatMessage
import com.opencell.ai.LlmException
import com.opencell.ai.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantChatPromptTest {
    @Test
    fun `stateless remote question is sent to the model`() {
        assertEquals(
            listOf(ChatMessage(Role.USER, "What is my next task?")),
            assistantConversationHistory("What is my next task?", emptyList()),
        )
    }

    @Test
    fun `app history already containing current question is not duplicated`() {
        val history = listOf(
            ChatMessage(Role.USER, "Hello"),
            ChatMessage(Role.ASSISTANT, "Hi there"),
            ChatMessage(Role.USER, "What is my next task?"),
        )
        assertEquals(history, assistantConversationHistory("What is my next task?", history))
    }

    @Test
    fun `repeated question after an answer is a new user turn`() {
        val history = listOf(ChatMessage(Role.USER, "Again?"), ChatMessage(Role.ASSISTANT, "Again?"))
        assertEquals(history + ChatMessage(Role.USER, "Again?"), assistantConversationHistory("Again?", history))
    }

    @Test
    fun `prompt keeps only the most recent history`() {
        val history = (1..41).map { ChatMessage(Role.USER, "message $it") }

        val messages = assistantConversationHistory("message 42", history)

        assertEquals(40, messages.size)
        assertEquals("message 3", messages.first().content)
        assertEquals("message 42", messages.last().content)
    }

    @Test
    fun `prompt does not start with an assistant turn`() {
        val messages = assistantConversationHistory(
            "new question",
            listOf(ChatMessage(Role.ASSISTANT, "old answer"), ChatMessage(Role.USER, "new question")),
        )

        assertEquals(listOf(Role.USER), messages.map { it.role })
    }

    @Test
    fun `trimming history drops orphaned assistant turn and retains current question`() {
        val history = listOf(ChatMessage(Role.USER, "old question"), ChatMessage(Role.ASSISTANT, "old answer")) +
            (1..39).map { ChatMessage(Role.USER, "question $it") }
        val messages = assistantConversationHistory("question 39", history)
        assertEquals(39, messages.size)
        assertEquals("question 1", messages.first().content)
        assertEquals("question 39", messages.last().content)
    }

    @Test
    fun `provider failures become visible assistant errors`() {
        assertTrue(NO_PROVIDER_MESSAGE.startsWith("No AI provider configured"))
        assertEquals("Assistant error: connection refused", assistantErrorMessage(LlmException("connection refused")))
        assertEquals("The assistant could not respond. Please try again.", assistantErrorMessage(Exception()))
        assertEquals("Assistant error: " + "x".repeat(300), assistantErrorMessage(Exception("x".repeat(400))))
    }

}
