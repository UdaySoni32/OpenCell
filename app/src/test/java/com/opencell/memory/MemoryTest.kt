package com.opencell.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTest {
    private fun fact(
        text: String,
        contact: String? = null,
        sourceType: MemorySourceType = MemorySourceType.USER_CONFIRMED,
        category: MemoryCategory = MemoryCategory.GENERAL,
        confidence: Double = 1.0,
        needsConfirmation: Boolean = false,
    ) = MemoryFact(
        contactKey = contact,
        text = text,
        source = "test",
        createdAtMillis = 1_000,
        category = category,
        sourceType = sourceType,
        confidence = confidence,
        updatedAtMillis = 1_000,
        needsConfirmation = needsConfirmation,
    )

    @Test
    fun `explicit remember command is parsed and categorized`() {
        assertEquals(
            RememberCommand("I prefer short replies in Hinglish", MemoryCategory.PREFERENCE),
            RememberCommandParser.parse("Remember that I prefer short replies in Hinglish."),
        )
        assertEquals(null, RememberCommandParser.parse("Remember"))
    }

    @Test
    fun `retrieval prefers matching contact and confirmed facts`() {
        val results = MemoryRetriever.retrieve(
            listOf(
                fact("Riya likes early dinners", contact = "9876543210"),
                fact("Arjun likes early dinners", contact = "9999999999"),
                fact("The user likes early dinners", sourceType = MemorySourceType.SMS, confidence = 0.7),
            ),
            contactKey = "9876543210",
            query = "dinner Riya",
        )
        assertEquals("Riya likes early dinners", results.first().text)
    }

    @Test
    fun `unconfirmed and sensitive facts stay out of normal context`() {
        val results = MemoryRetriever.retrieve(
            listOf(
                fact("Possible preference", sourceType = MemorySourceType.SMS, confidence = 0.8, needsConfirmation = true),
                fact("Medical detail", category = MemoryCategory.SENSITIVE),
                fact("Prefers short messages"),
            ),
            contactKey = null,
            query = "preferences",
        )
        assertEquals(listOf("Prefers short messages"), results.map { it.text })
        assertTrue(results.none { it.needsConfirmation })
    }
}
