package com.opencell.assistant

import com.opencell.ai.AiSettingsStore
import com.opencell.ai.ChatMessage
import com.opencell.ai.LlmException
import com.opencell.ai.Role
import com.opencell.memory.MemoryDao
import com.opencell.memory.MemoryRetriever
import com.opencell.memory.MemoryCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.long

sealed interface AssistantIntent {
    data class Answer(val message: String) : AssistantIntent
    data class AskClarification(val message: String) : AssistantIntent
    data class Remember(val text: String, val category: MemoryCategory) : AssistantIntent
    data class Forget(val text: String) : AssistantIntent
    data class SendSms(val to: String, val body: String, val reason: String?) : AssistantIntent
    data class CreateTask(val title: String, val details: String?, val dueAtMillis: Long?) : AssistantIntent
    data class CompleteTask(val title: String) : AssistantIntent
    data class CreateReminder(val title: String, val triggerAtMillis: Long) : AssistantIntent
}

data class AssistantDecision(val intent: AssistantIntent, val raw: String)

object AssistantIntentParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    /** Strict parser: malformed model output is rejected instead of becoming an accidental action. */
    fun parse(raw: String): AssistantIntent? = runCatching {
        val root = json.parseToJsonElement(raw.trim()).jsonObject
        val type = root["type"]?.jsonPrimitive?.content ?: return null
        when (type) {
            "answer" -> AssistantIntent.Answer(required(root, "message", 4000))
            "clarify" -> AssistantIntent.AskClarification(required(root, "message", 1000))
            "remember" -> AssistantIntent.Remember(
                required(root, "text", 300),
                root["category"]?.jsonPrimitive?.content?.let { value ->
                    runCatching { MemoryCategory.valueOf(value.uppercase()) }.getOrDefault(MemoryCategory.GENERAL)
                } ?: MemoryCategory.GENERAL,
            )
            "forget" -> AssistantIntent.Forget(required(root, "text", 300))
            "send_sms" -> {
                val to = required(root, "to", 40)
                require(isPhoneNumber(to))
                AssistantIntent.SendSms(to, required(root, "body", 1600), root["reason"]?.jsonPrimitive?.content?.take(500))
            }
            "create_task" -> AssistantIntent.CreateTask(
                required(root, "title", 300),
                root["details"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }?.take(1000),
                root["due_at_millis"]?.jsonPrimitive?.longOrNull,
            )
            "complete_task" -> AssistantIntent.CompleteTask(required(root, "title", 300))
            "create_reminder" -> AssistantIntent.CreateReminder(
                required(root, "title", 300),
                root["trigger_at_millis"]?.jsonPrimitive?.long ?: error("trigger_at_millis is required"),
            )
            else -> null
        }
    }.getOrNull()

    private fun required(root: Map<String, kotlinx.serialization.json.JsonElement>, key: String, max: Int): String {
        val value = root[key]?.jsonPrimitive?.content?.trim().orEmpty()
        require(value.isNotBlank() && value.length <= max)
        return value
    }

    private fun isPhoneNumber(value: String): Boolean =
        Regex("^\\+?[0-9 ()\\-]{3,20}$").matches(value) && value.count(Char::isDigit) >= 3
}

class AssistantOrchestrator(
    private val ai: AiSettingsStore,
    private val memory: MemoryDao,
) {
    suspend fun decide(userText: String, history: List<ChatMessage>): Result<AssistantDecision> {
        val llm = ai.buildLlm() ?: return Result.failure(LlmException(NO_PROVIDER_MESSAGE))
        val settings = ai.current()
        val instructions = ai.currentInstructions()
        val facts = withContext(Dispatchers.IO) {
            MemoryRetriever.retrieve(memory.relevant(null, limit = 80), null, userText, limit = 20)
        }
        val prompt = structuredPrompt(settings.userName, instructions, facts)
        return runCatching {
            val raw = withContext(Dispatchers.IO) { llm.complete(prompt, history.takeLast(MAX_HISTORY), 500).trim() }
            val intent = AssistantIntentParser.parse(raw)
                ?: throw LlmException("The assistant returned an unsafe or invalid response")
            AssistantDecision(intent, raw)
        }
    }

    private fun structuredPrompt(userName: String, instructions: String, facts: List<com.opencell.memory.MemoryFact>): String = buildString {
        val owner = userName.ifBlank { "the phone's owner" }
        appendLine("You are $owner's personal assistant inside OpenCell.")
        appendLine("Return exactly one JSON object and no markdown or commentary.")
        appendLine("Allowed types:")
        appendLine("answer: {\"type\":\"answer\",\"message\":\"...\"}")
        appendLine("clarify: {\"type\":\"clarify\",\"message\":\"...\"}")
        appendLine("remember: {\"type\":\"remember\",\"text\":\"...\",\"category\":\"PREFERENCE|IDENTITY|RELATIONSHIP|SCHEDULE|GOAL|COMMITMENT|INSTRUCTION|GENERAL\"}")
        appendLine("forget: {\"type\":\"forget\",\"text\":\"...\"}")
        appendLine("send_sms: {\"type\":\"send_sms\",\"to\":\"+...\",\"body\":\"...\",\"reason\":\"...\"}")
        appendLine("create_task: {\"type\":\"create_task\",\"title\":\"...\",\"details\":\"...\",\"due_at_millis\":123}")
        appendLine("complete_task: {\"type\":\"complete_task\",\"title\":\"...\"}")
        appendLine("create_reminder: {\"type\":\"create_reminder\",\"title\":\"...\",\"trigger_at_millis\":123}")
        appendLine("Current UTC epoch millis: ${System.currentTimeMillis()}")
        appendLine("Never claim an SMS was sent. send_sms is only a draft for the user to review. Never invent a phone number.")
        if (instructions.isNotBlank()) appendLine("$owner's instructions:\n${instructions.trim()}")
        if (facts.isNotEmpty()) appendLine("Relevant confirmed memories:\n" + facts.joinToString("\n") { "- ${it.text}" })
    }.trim()

    companion object {
        private const val MAX_HISTORY = 40
    }
}
