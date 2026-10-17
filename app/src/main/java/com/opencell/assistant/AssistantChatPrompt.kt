package com.opencell.assistant

import com.opencell.ai.AiSettings
import com.opencell.ai.ChatMessage
import com.opencell.ai.Role

data class AssistantPrompt(val system: String, val messages: List<ChatMessage>)

const val NO_PROVIDER_MESSAGE = "No AI provider configured. Open Settings → AI assistant to configure one."

/** Builds the Phase 1 chat prompt. It deliberately gives the model no action-execution interface. */
fun buildAssistantPrompt(settings: AiSettings, instructions: String, history: List<ChatMessage>): AssistantPrompt {
    val owner = settings.userName.ifBlank { "the phone's owner" }
    val system = buildString {
        appendLine("You are OpenCell's personal assistant for $owner.")
        appendLine("Answer clearly and helpfully, and be honest when you do not know something.")
        appendLine("This is an informational chat in Phase 1. Do not send SMS, place calls, or perform any other external action.")
        appendLine("Never claim that an external action happened. If the user asks for one, explain that it has not been executed.")
        if (instructions.isNotBlank()) appendLine("$owner's instructions:\n${instructions.trim()}")
    }.trim()
    return AssistantPrompt(system, history.takeLast(MAX_HISTORY).dropWhile { it.role == Role.ASSISTANT })
}

fun assistantErrorMessage(error: Throwable): String {
    val detail = error.message.orEmpty().trim().take(300)
    return if (detail.isBlank()) "The assistant could not respond. Please try again." else "Assistant error: $detail"
}

private const val MAX_HISTORY = 40
