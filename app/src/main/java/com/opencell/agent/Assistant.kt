package com.opencell.agent

import android.util.Log
import com.opencell.ai.AiSettingsStore
import com.opencell.ai.ChatMessage
import com.opencell.ai.LlmException
import com.opencell.ai.LlmProvider
import com.opencell.ai.Role
import com.opencell.contacts.ContactsRepository
import com.opencell.memory.MemoryDao
import com.opencell.memory.MemoryFact
import com.opencell.memory.PromptBuilder
import com.opencell.memory.ThreadLine
import com.opencell.policy.ActionExecutor
import com.opencell.policy.ActionPolicy
import com.opencell.policy.ActionRequest
import com.opencell.policy.ActionType
import com.opencell.policy.Actor
import com.opencell.policy.PolicySettings
import com.opencell.sms.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Tells the user what the assistant did or why it couldn't. */
interface AssistantNotifier {
    fun info(title: String, text: String)
}

/**
 * Text-first assistant: drafts SMS replies, screens unknown callers over SMS, and learns facts.
 * It never sends anything itself; every SMS/call goes through [ActionExecutor] and therefore the user's policy.
 */
class Assistant(
    private val ai: AiSettingsStore,
    private val llmFactory: suspend () -> LlmProvider?,
    private val sms: SmsRepository,
    private val contacts: ContactsRepository,
    private val memory: MemoryDao,
    private val executor: ActionExecutor,
    private val policy: suspend () -> PolicySettings,
    private val notifier: AssistantNotifier,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val screeningThreads = ConcurrentHashMap.newKeySet<String>()
    private val recentReplies = ConcurrentHashMap<String, MutableList<Long>>()

    suspend fun onIncomingSms(from: String, body: String) {
        val settings = ai.current()
        val key = ActionPolicy.numberKey(from) ?: return
        val contactName = withContext(Dispatchers.IO) { contacts.nameFor(from) }
        val screening = key in screeningThreads
        if (screening) notifier.info("Screened caller ${contactName ?: from}", body)

        val skip = AgentRules.smsSkipReason(from, body, settings, contactName != null, screening, repliesLastHour(key))
        if (skip == null) reply(from, contactName ?: from, key, screening)
        else Log.i(TAG, "not replying to $from: $skip")

        if (AgentRules.shouldLearn(from, body, settings, contactName != null)) learn(from, contactName ?: from, key)
    }

    /** Incoming call from a number that should be screened: decline it, then open an SMS conversation. */
    suspend fun onRingingCall(callId: String, number: String?) {
        val settings = ai.current()
        val isContact = number?.let { withContext(Dispatchers.IO) { contacts.nameFor(it) } } != null
        val allowlisted = number != null && ActionPolicy.isAllowed(number, policy().allowlist)
        if (!AgentRules.shouldScreenCall(number, settings, isContact, allowlisted) || number == null) return
        executor.submit(ActionRequest(Actor.Ai(SCREENING), ActionType.REJECT_CALL, callId))
        val key = ActionPolicy.numberKey(number) ?: return
        screeningThreads += key
        reply(number, number, key, screening = true)
    }

    private suspend fun reply(to: String, label: String, key: String, screening: Boolean) {
        val llm = llmFactory() ?: return
        val settings = ai.current()
        val history = history(to).ifEmpty { listOf(ThreadLine(true, "(I just called and was declined. Write the first SMS to me.)")) }
        val prompt = PromptBuilder.smsReply(settings.userName, ai.currentInstructions(), memory.relevant(key), label, history, screening)
        val text = try {
            withContext(Dispatchers.IO) { llm.complete(prompt.system, prompt.messages) }
        } catch (e: LlmException) {
            notifier.info("Assistant couldn't reply to $label", e.message.orEmpty().take(200))
            return
        }
        val replyText = PromptBuilder.cleanReply(text) ?: return
        recentReplies.getOrPut(key) { mutableListOf() }.let { synchronized(it) { it.add(now()) } }
        executor.submit(ActionRequest(Actor.Ai(if (screening) SCREENING else SMS_REPLIES), ActionType.SEND_SMS, to, replyText))
    }

    private suspend fun learn(from: String, label: String, key: String) {
        val llm = llmFactory() ?: return
        val prompt = PromptBuilder.factExtraction(label, history(from))
        val answer = runCatching { withContext(Dispatchers.IO) { llm.complete(prompt.system, prompt.messages, maxTokens = 300) } }
            .getOrElse { Log.w(TAG, "fact extraction failed", it); return }
        val known = memory.relevant(key, limit = 200).map { it.text.lowercase() }.toSet()
        PromptBuilder.parseFacts(answer, label).filter { (_, text) -> text.lowercase() !in known }.forEach { (aboutUser, text) ->
            memory.insert(MemoryFact(contactKey = if (aboutUser) null else key, text = text, source = "SMS with $label", createdAtMillis = now()))
        }
    }

    private suspend fun history(address: String): List<ThreadLine> {
        return sms.threadMessages(sms.threadIdFor(address), limit = 24).reversed().map { ThreadLine(it.isIncoming, it.body) }
    }

    private fun repliesLastHour(key: String): Int {
        val cutoff = now() - 60 * 60_000L
        val list = recentReplies[key] ?: return 0
        synchronized(list) {
            list.removeAll { it < cutoff }
            return list.size
        }
    }

    /** Lets the user test their provider setup from settings. */
    suspend fun testProvider(): String {
        val llm = llmFactory() ?: return "No provider configured"
        return try {
            val answer = withContext(Dispatchers.IO) { llm.complete("Reply with one short friendly sentence.", listOf(ChatMessage(Role.USER, "Say hello."))) }
            "OK (${llm.label}): $answer"
        } catch (e: LlmException) {
            "Failed: ${e.message}"
        }
    }

    private companion object {
        const val TAG = "Assistant"
        const val SMS_REPLIES = "SMS replies"
        const val SCREENING = "call screening"
    }
}
