package com.opencell.assistant

import com.opencell.ai.ChatMessage
import com.opencell.ai.Role

const val NO_PROVIDER_MESSAGE = "No AI provider configured. Open Settings → AI assistant to configure one."

/**
 * Model history ends with the current user turn exactly once. UI history already includes that
 * turn; stateless callers may supply an empty history. Earlier equal questions are separate turns.
 */
fun assistantConversationHistory(
    userText: String,
    history: List<ChatMessage>,
): List<ChatMessage> {
    val current = ChatMessage(Role.USER, userText)
    val conversation = if (history.lastOrNull() == current) history else history + current
    return conversation.takeLast(MAX_HISTORY).dropWhile { it.role == Role.ASSISTANT }
}

fun assistantErrorMessage(error: Throwable): String {
    val detail = error.message.orEmpty().trim().take(300)
    return if (detail.isBlank()) "The assistant could not respond. Please try again." else "Assistant error: $detail"
}

private const val MAX_HISTORY = 40
