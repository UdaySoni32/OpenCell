package com.opencell.memory

import org.junit.Assert.assertTrue
import org.junit.Test

class LocalVoicePromptTest {
    @Test
    fun `local voice prompt includes the optional goal and standing instructions`() {
        val prompt = PromptBuilder.localVoice("Uday", "Keep answers brief", "Plan my afternoon")

        assertTrue(prompt.contains("private, in-app voice conversation"))
        assertTrue(prompt.contains("The user's goal for this conversation is: Plan my afternoon"))
        assertTrue(prompt.contains("Uday's standing instructions:\nKeep answers brief"))
    }
}
