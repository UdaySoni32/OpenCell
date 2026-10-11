package com.opencell.agent

import com.opencell.ai.AiSettings
import com.opencell.ai.ProviderConfig
import com.opencell.ai.ProviderKind
import com.opencell.ai.Role
import com.opencell.memory.MemoryFact
import com.opencell.memory.PromptBuilder
import com.opencell.memory.ThreadLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLogicTest {

    private val provider = ProviderConfig(ProviderKind.OPENAI_COMPATIBLE, "http://x/v1", "m")
    private val on = AiSettings(primary = provider, smsAgent = true, smsAgentContactsOnly = true, learnFromSms = true, screenUnknownCalls = true)

    private fun skip(from: String = "+919876543210", body: String = "are we meeting?", s: AiSettings = on, contact: Boolean = true, screening: Boolean = false, replies: Int = 0) =
        AgentRules.smsSkipReason(from, body, s, contact, screening, replies)

    @Test
    fun `replies to contacts when enabled`() {
        assertNull(skip())
    }

    @Test
    fun `never replies to banks, short codes or OTPs`() {
        assertEquals("business or short-code sender", skip(from = "JM-HDFCBK"))
        assertEquals("business or short-code sender", skip(from = "56767"))
        assertEquals("looks like a one-time code", skip(body = "Your OTP is 482913. Do not share."))
        assertEquals("looks like a one-time code", skip(body = "Use code 4829 to verify"))
    }

    @Test
    fun `respects off switch, contacts-only and loop limit`() {
        assertEquals("SMS replies are off", skip(s = on.copy(smsAgent = false)))
        assertEquals("no AI provider configured", skip(s = on.copy(primary = null)))
        assertEquals("sender is not in contacts", skip(contact = false))
        assertNull("screening threads are allowed for strangers", skip(contact = false, screening = true, s = on.copy(smsAgent = false)))
        assertEquals("reply limit reached for this conversation", skip(replies = AgentRules.MAX_AI_REPLIES_PER_HOUR))
    }

    @Test
    fun `call screening only for unknown personal numbers`() {
        assertTrue(AgentRules.shouldScreenCall("+15550001111", on, isContact = false, isAllowlisted = false))
        assertFalse(AgentRules.shouldScreenCall("+15550001111", on, isContact = true, isAllowlisted = false))
        assertFalse(AgentRules.shouldScreenCall("+15550001111", on, isContact = false, isAllowlisted = true))
        assertFalse(AgentRules.shouldScreenCall(null, on, isContact = false, isAllowlisted = false))
        assertFalse(AgentRules.shouldScreenCall("+15550001111", on.copy(screenUnknownCalls = false), isContact = false, isAllowlisted = false))
    }

    @Test
    fun `prompt includes instructions and both kinds of facts`() {
        val facts = listOf(
            MemoryFact(contactKey = null, text = "Uday is a student", source = "s", createdAtMillis = 0),
            MemoryFact(contactKey = "9876543210", text = "Riya is his sister", source = "s", createdAtMillis = 0),
        )
        val p = PromptBuilder.smsReply("Uday", "Reply in Hinglish", facts, "Riya", listOf(ThreadLine(true, "kahan ho?")), screening = false)
        assertTrue(p.system.contains("Reply in Hinglish"))
        assertTrue(p.system.contains("What you know about Uday:\n- Uday is a student"))
        assertTrue(p.system.contains("What you know about Riya:\n- Riya is his sister"))
        assertTrue(p.system.contains(PromptBuilder.NO_REPLY))
        assertEquals(Role.USER, p.messages.single().role)
    }

    @Test
    fun `history merges same-side turns and never starts with assistant`() {
        val p = PromptBuilder.smsReply("", "", emptyList(), "X", listOf(ThreadLine(false, "mine"), ThreadLine(true, "a"), ThreadLine(true, "b"), ThreadLine(false, "c")), false)
        assertEquals(listOf(Role.USER, Role.ASSISTANT), p.messages.map { it.role })
        assertEquals("a\nb", p.messages.first().content)
    }

    @Test
    fun `reply cleaning`() {
        assertEquals("On my way", PromptBuilder.cleanReply("  \"On my way\" "))
        assertNull(PromptBuilder.cleanReply("NO_REPLY"))
        assertNull(PromptBuilder.cleanReply("   "))
    }

    @Test
    fun `fact parsing drops secrets and junk`() {
        val facts = PromptBuilder.parseFacts(
            """
            USER: Prefers to be called after 6pm
            - CONTACT: Riya is his sister
            CONTACT: Her OTP is 123456
            USER: ok
            random line
            """.trimIndent(),
        )
        assertEquals(listOf(true to "Prefers to be called after 6pm", false to "Riya is his sister"), facts)
        assertTrue(PromptBuilder.parseFacts("NONE").isEmpty())
    }

    @Test
    fun `user facts that name the contact are moved to the contact`() {
        val facts = PromptBuilder.parseFacts("USER: Riya is vegetarian.\nUSER: Likes early mornings", "Riya")
        assertEquals(listOf(false to "Riya is vegetarian.", true to "Likes early mornings"), facts)
        assertEquals(listOf(true to "Lives in Hyderabad"), PromptBuilder.parseFacts("USER: Lives in Hyderabad", "5552221111"))
    }
}
