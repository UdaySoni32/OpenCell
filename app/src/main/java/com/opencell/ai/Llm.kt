package com.opencell.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.HttpURLConnection
import java.net.URI

enum class Role { USER, ASSISTANT }

data class ChatMessage(val role: Role, val content: String)

class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface LlmProvider {
    val label: String

    /** Blocking network call; run on Dispatchers.IO. */
    fun complete(system: String, messages: List<ChatMessage>, maxTokens: Int = 400): String
}

data class HttpResult(val code: Int, val body: String)

fun interface HttpPost {
    fun post(url: String, headers: Map<String, String>, jsonBody: String): HttpResult
}

/** Plain HttpURLConnection so the app needs no HTTP client dependency. */
object UrlConnectionHttp : HttpPost {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 90_000 // local models on a Pi can be slow

    override fun post(url: String, headers: Map<String, String>, jsonBody: String): HttpResult {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(jsonBody.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            HttpResult(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }
}

private val json = Json { ignoreUnknownKeys = true }

private fun parseOrThrow(label: String, result: HttpResult): JsonObject {
    if (result.code !in 200..299) throw LlmException("$label returned HTTP ${result.code}: ${result.body.take(200)}")
    return runCatching { json.parseToJsonElement(result.body).jsonObject }
        .getOrElse { throw LlmException("$label returned invalid JSON", it) }
}

/** Any OpenAI-style `/chat/completions` endpoint: Ollama, OpenAI, Groq, OpenRouter, Gemini's OpenAI endpoint, LM Studio… */
class OpenAiCompatibleLlm(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String?,
    private val http: HttpPost = UrlConnectionHttp,
) : LlmProvider {
    override val label = "$model @ ${baseUrl.removePrefix("https://").removePrefix("http://").substringBefore('/')}"

    override fun complete(system: String, messages: List<ChatMessage>, maxTokens: Int): String {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("temperature", 0.4)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", system) }
                messages.forEach { m -> addJsonObject { put("role", m.role.name.lowercase()); put("content", m.content) } }
            }
        }
        val headers = apiKey?.takeIf { it.isNotBlank() }?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
        val res = parseOrThrow(label, http.post(baseUrl.trimEnd('/') + "/chat/completions", headers, body.toString()))
        return runCatching { res["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content }
            .getOrNull()?.trim() ?: throw LlmException("$label: unexpected response shape")
    }
}

/** Anthropic Messages API. */
class AnthropicLlm(
    private val apiKey: String,
    private val model: String,
    private val http: HttpPost = UrlConnectionHttp,
    private val baseUrl: String = "https://api.anthropic.com",
) : LlmProvider {
    override val label = "$model @ Anthropic"

    override fun complete(system: String, messages: List<ChatMessage>, maxTokens: Int): String {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("system", system)
            putJsonArray("messages") {
                messages.forEach { m -> addJsonObject { put("role", m.role.name.lowercase()); put("content", m.content) } }
            }
        }
        val headers = mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01")
        val res = parseOrThrow(label, http.post("$baseUrl/v1/messages", headers, body.toString()))
        val blocks = runCatching { res["content"]?.jsonArray?.map { it.jsonObject } }.getOrNull()
            ?: throw LlmException("$label: unexpected response shape")
        return blocks.filter { it["type"]?.jsonPrimitive?.content == "text" }
            .joinToString("") { it["text"]?.jsonPrimitive?.content.orEmpty() }.trim()
    }
}

/** Tries each provider in order (e.g. cloud, then your own server). */
class FallbackLlm(private val providers: List<LlmProvider>) : LlmProvider {
    override val label = providers.joinToString(" → ") { it.label }

    override fun complete(system: String, messages: List<ChatMessage>, maxTokens: Int): String {
        if (providers.isEmpty()) throw LlmException("no AI provider configured")
        val errors = mutableListOf<String>()
        for (p in providers) {
            try {
                return p.complete(system, messages, maxTokens)
            } catch (e: LlmException) {
                errors += e.message.orEmpty()
            } catch (e: java.io.IOException) {
                errors += "${p.label}: ${e.javaClass.simpleName} ${e.message.orEmpty()}"
            }
        }
        throw LlmException(errors.joinToString("; "))
    }
}
