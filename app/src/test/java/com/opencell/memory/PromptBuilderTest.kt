package com.opencell.memory

import com.opencell.ai.ChatMessage
import com.opencell.ai.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    @Test
    fun `smsReply builds correct system prompt and messages`() {
        val facts = listOf(
            MemoryFact(id = 1, contactKey = null, text = "User is a programmer", source = "System", createdAtMillis = 0),
            MemoryFact(id = 2, contactKey = "123", text = "Contact is a manager", source = "System", createdAtMillis = 0)
        )
        val history = listOf(
            ThreadLine(fromThem = true, text = "Hi"),
            ThreadLine(fromThem = false, text = "Hello"),
            ThreadLine(fromThem = true, text = "How are you?")
        )

        val prompt = PromptBuilder.smsReply(
            userName = "Alice",
            instructions = "Be polite",
            facts = facts,
            contactLabel = "Bob",
            history = history,
            screening = false
        )

        // Check messages (history mapped to ChatMessage, consecutive turns handled)
        // History: them ("Hi"), us ("Hello"), them ("How are you?")
        val expectedMessages = listOf(
            ChatMessage(Role.USER, "Hi"),
            ChatMessage(Role.ASSISTANT, "Hello"),
            ChatMessage(Role.USER, "How are you?")
        )
        assertEquals(expectedMessages, prompt.messages)

        // Check system prompt contents
        assertTrue(prompt.system.contains("You are Alice's assistant replying by SMS to Bob on Alice's behalf."))
        assertTrue(prompt.system.contains("Alice's instructions:\nBe polite"))
        assertTrue(prompt.system.contains("What you know about Alice:\n- User is a programmer"))
        assertTrue(prompt.system.contains("What you know about Bob:\n- Contact is a manager"))
        assertTrue(prompt.system.contains("NO_REPLY"))
    }

    @Test
    fun `smsReply builds correct system prompt for screening`() {
        val prompt = PromptBuilder.smsReply(
            userName = "Charlie",
            instructions = "",
            facts = emptyList(),
            contactLabel = "Unknown Number",
            history = emptyList(),
            screening = true
        )

        assertTrue(prompt.system.contains("You are Charlie's assistant. Charlie declined a call from Unknown Number, who is not in their contacts."))
        assertTrue(prompt.system.contains("Politely find out who they are and what they want"))
    }

    @Test
    fun `smsReply handles blank user name`() {
        val prompt = PromptBuilder.smsReply(
            userName = "   ",
            instructions = "",
            facts = emptyList(),
            contactLabel = "David",
            history = emptyList(),
            screening = false
        )

        assertTrue(prompt.system.contains("You are the phone's owner's assistant replying by SMS to David on the phone's owner's behalf."))
    }
}
