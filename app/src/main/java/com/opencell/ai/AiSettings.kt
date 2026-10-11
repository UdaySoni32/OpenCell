package com.opencell.ai

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ProviderKind { OPENAI_COMPATIBLE, ANTHROPIC }

data class ProviderConfig(val kind: ProviderKind, val baseUrl: String, val model: String)

/** One-tap starting points; users can edit any field. */
enum class ProviderPreset(val label: String, val config: ProviderConfig, val needsKey: Boolean) {
    OLLAMA("Your server (Ollama)", ProviderConfig(ProviderKind.OPENAI_COMPATIBLE, "http://100.64.0.1:11434/v1", "gemma3:4b"), false),
    OPENAI("OpenAI", ProviderConfig(ProviderKind.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "gpt-4o-mini"), true),
    ANTHROPIC("Anthropic Claude", ProviderConfig(ProviderKind.ANTHROPIC, "https://api.anthropic.com", "claude-haiku-5-5"), true),
    GEMINI("Google Gemini", ProviderConfig(ProviderKind.OPENAI_COMPATIBLE, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"), true),
    GROQ("Groq", ProviderConfig(ProviderKind.OPENAI_COMPATIBLE, "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"), true),
}

enum class SpeechKind { ON_DEVICE, OPENAI_COMPATIBLE }

/** [model] is the STT/TTS model; [voice] only matters for TTS. Ignored when [kind] is ON_DEVICE. */
data class SpeechConfig(val kind: SpeechKind = SpeechKind.ON_DEVICE, val baseUrl: String = "", val model: String = "", val voice: String = "")

enum class SpeechPreset(val label: String, val stt: SpeechConfig, val tts: SpeechConfig) {
    ON_DEVICE("On this phone", SpeechConfig(), SpeechConfig()),
    BRAIN(
        "Your server (opencell brain)",
        SpeechConfig(SpeechKind.OPENAI_COMPATIBLE, "http://100.64.0.1:8788/v1", "whisper"),
        SpeechConfig(SpeechKind.OPENAI_COMPATIBLE, "http://100.64.0.1:8788/v1", "piper", "default"),
    ),
    OPENAI(
        "OpenAI",
        SpeechConfig(SpeechKind.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "whisper-1"),
        SpeechConfig(SpeechKind.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "tts-1", "alloy"),
    ),
}

data class AiSettings(
    val primary: ProviderConfig? = null,
    val fallback: ProviderConfig? = null,
    /** Reply to incoming SMS (every reply still goes through the permission policy). */
    val smsAgent: Boolean = false,
    /** Only for people in contacts (safer), or anyone. */
    val smsAgentContactsOnly: Boolean = true,
    /** Decline calls from numbers not in contacts and continue by SMS. */
    val screenUnknownCalls: Boolean = false,
    /** Extract facts from SMS conversations into memory. */
    val learnFromSms: Boolean = false,
    val userName: String = "",
    val stt: SpeechConfig = SpeechConfig(),
    val tts: SpeechConfig = SpeechConfig(),
    /** Let the AI answer calls you don't pick up within [autoPickupSeconds]. */
    val autoPickup: Boolean = false,
    val autoPickupSeconds: Int = 20,
    /** Transcribe calls you answer yourself (Shizuku mode only) and learn facts from them. */
    val learnFromCalls: Boolean = false,
)

private val Context.aiStore by preferencesDataStore(name = "ai_settings")

class AiSettingsStore(private val context: Context, private val secrets: KeystoreSecrets) {

    val settings: Flow<AiSettings> = context.aiStore.data.map { it.toSettings() }
    val instructions: Flow<String> = context.aiStore.data.map { it[INSTRUCTIONS].orEmpty() }

    suspend fun current(): AiSettings = settings.first()
    suspend fun currentInstructions(): String = instructions.first()

    suspend fun update(transform: (AiSettings) -> AiSettings) {
        context.aiStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs.putProvider("primary", next.primary)
            prefs.putProvider("fallback", next.fallback)
            prefs[SMS_AGENT] = next.smsAgent
            prefs[SMS_CONTACTS_ONLY] = next.smsAgentContactsOnly
            prefs[SCREEN_CALLS] = next.screenUnknownCalls
            prefs[LEARN_SMS] = next.learnFromSms
            prefs[USER_NAME] = next.userName.trim()
            prefs.putSpeech("stt", next.stt)
            prefs.putSpeech("tts", next.tts)
            prefs[AUTO_PICKUP] = next.autoPickup
            prefs[AUTO_PICKUP_SECONDS] = next.autoPickupSeconds.coerceIn(5, 60).toString()
            prefs[LEARN_CALLS] = next.learnFromCalls
        }
    }

    /** Speech engines for a voice session; on-device engines need the Android context, so the caller builds those. */
    fun speechKey(slot: String): String? = secrets.get("ai_key_$slot")

    suspend fun setInstructions(text: String) {
        context.aiStore.edit { it[INSTRUCTIONS] = text.take(MAX_INSTRUCTIONS) }
    }

    fun setKey(slot: String, key: String?) = secrets.put("ai_key_$slot", key)
    fun hasKey(slot: String) = secrets.has("ai_key_$slot")

    /** The configured providers as a fallback chain, or null if none is set up. */
    suspend fun buildLlm(): LlmProvider? {
        val s = current()
        val providers = listOfNotNull(s.primary?.let { toProvider(it, "primary") }, s.fallback?.let { toProvider(it, "fallback") })
        return providers.takeIf { it.isNotEmpty() }?.let(::FallbackLlm)
    }

    private fun toProvider(c: ProviderConfig, slot: String): LlmProvider? {
        val key = secrets.get("ai_key_$slot")
        return when (c.kind) {
            ProviderKind.OPENAI_COMPATIBLE -> OpenAiCompatibleLlm(c.baseUrl, c.model, key)
            ProviderKind.ANTHROPIC -> key?.let { AnthropicLlm(it, c.model, baseUrl = c.baseUrl) }
        }
    }

    private fun Preferences.toSettings() = AiSettings(
        primary = provider("primary"),
        fallback = provider("fallback"),
        smsAgent = this[SMS_AGENT] ?: false,
        smsAgentContactsOnly = this[SMS_CONTACTS_ONLY] ?: true,
        screenUnknownCalls = this[SCREEN_CALLS] ?: false,
        learnFromSms = this[LEARN_SMS] ?: false,
        userName = this[USER_NAME].orEmpty(),
        stt = speech("stt"),
        tts = speech("tts"),
        autoPickup = this[AUTO_PICKUP] ?: false,
        autoPickupSeconds = this[AUTO_PICKUP_SECONDS]?.toIntOrNull() ?: 20,
        learnFromCalls = this[LEARN_CALLS] ?: false,
    )

    private fun Preferences.speech(slot: String): SpeechConfig {
        val kind = this[stringPreferencesKey("${slot}_kind")]?.let { runCatching { SpeechKind.valueOf(it) }.getOrNull() } ?: SpeechKind.ON_DEVICE
        return SpeechConfig(
            kind,
            this[stringPreferencesKey("${slot}_url")].orEmpty(),
            this[stringPreferencesKey("${slot}_model")].orEmpty(),
            this[stringPreferencesKey("${slot}_voice")].orEmpty(),
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.putSpeech(slot: String, c: SpeechConfig) {
        this[stringPreferencesKey("${slot}_kind")] = c.kind.name
        this[stringPreferencesKey("${slot}_url")] = c.baseUrl.trim()
        this[stringPreferencesKey("${slot}_model")] = c.model.trim()
        this[stringPreferencesKey("${slot}_voice")] = c.voice.trim()
    }

    private fun Preferences.provider(slot: String): ProviderConfig? {
        val kind = this[stringPreferencesKey("${slot}_kind")]?.let { runCatching { ProviderKind.valueOf(it) }.getOrNull() } ?: return null
        return ProviderConfig(kind, this[stringPreferencesKey("${slot}_url")].orEmpty(), this[stringPreferencesKey("${slot}_model")].orEmpty())
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.putProvider(slot: String, c: ProviderConfig?) {
        val keys = listOf("kind", "url", "model").map { stringPreferencesKey("${slot}_$it") }
        if (c == null) {
            keys.forEach { remove(it) }
        } else {
            this[keys[0]] = c.kind.name
            this[keys[1]] = c.baseUrl.trim()
            this[keys[2]] = c.model.trim()
        }
    }

    private companion object {
        const val MAX_INSTRUCTIONS = 4000
        val INSTRUCTIONS = stringPreferencesKey("instructions")
        val SMS_AGENT = booleanPreferencesKey("sms_agent")
        val SMS_CONTACTS_ONLY = booleanPreferencesKey("sms_contacts_only")
        val SCREEN_CALLS = booleanPreferencesKey("screen_unknown_calls")
        val LEARN_SMS = booleanPreferencesKey("learn_from_sms")
        val USER_NAME = stringPreferencesKey("user_name")
        val AUTO_PICKUP = booleanPreferencesKey("auto_pickup")
        val AUTO_PICKUP_SECONDS = stringPreferencesKey("auto_pickup_seconds")
        val LEARN_CALLS = booleanPreferencesKey("learn_from_calls")
    }
}
