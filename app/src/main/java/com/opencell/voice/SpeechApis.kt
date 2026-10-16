package com.opencell.voice

import com.opencell.ai.LlmException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.UUID

/** Raw HTTP for speech endpoints (multipart upload / binary download). Swappable in tests. */
interface SpeechHttp {
    fun postMultipart(url: String, headers: Map<String, String>, fields: Map<String, String>, fileField: String, fileName: String, file: ByteArray): Pair<Int, ByteArray>
    fun postJson(url: String, headers: Map<String, String>, json: String): Pair<Int, ByteArray>
}

object UrlConnectionSpeechHttp : SpeechHttp {
    private fun open(url: String, headers: Map<String, String>): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 60_000
            doOutput = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

    private fun HttpURLConnection.read(): Pair<Int, ByteArray> {
        val code = responseCode
        val body = (if (code >= 400) errorStream else inputStream)?.use { it.readBytes() } ?: ByteArray(0)
        return code to body
    }

    override fun postMultipart(url: String, headers: Map<String, String>, fields: Map<String, String>, fileField: String, fileName: String, file: ByteArray): Pair<Int, ByteArray> {
        val boundary = "----opencell" + UUID.randomUUID().toString().replace("-", "")
        val conn = open(url, headers + ("Content-Type" to "multipart/form-data; boundary=$boundary"))
        return try {
            conn.outputStream.use { out ->
                fields.forEach { (k, v) -> out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n".toByteArray()) }
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$fileField\"; filename=\"$fileName\"\r\nContent-Type: audio/wav\r\n\r\n".toByteArray())
                out.write(file)
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            conn.read()
        } finally {
            conn.disconnect()
        }
    }

    override fun postJson(url: String, headers: Map<String, String>, json: String): Pair<Int, ByteArray> {
        val conn = open(url, headers + ("Content-Type" to "application/json"))
        return try {
            conn.outputStream.use { it.write(json.toByteArray()) }
            conn.read()
        } finally {
            conn.disconnect()
        }
    }
}

private fun authHeaders(apiKey: String?) = apiKey?.takeIf { it.isNotBlank() }?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()

/** OpenAI-style `/audio/transcriptions`: OpenAI (whisper-1), Groq (whisper-large-v3), or `opencell brain`. */
class OpenAiCompatibleStt(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String?,
    private val http: SpeechHttp = UrlConnectionSpeechHttp,
) : SttEngine {
    override val label = "$model (speech-to-text)"

    override fun transcribe(pcm: Pcm): String {
        val (code, body) = http.postMultipart(
            baseUrl.trimEnd('/') + "/audio/transcriptions",
            authHeaders(apiKey),
            mapOf("model" to model, "response_format" to "json"),
            "file",
            "speech.wav",
            Wav.encode(pcm),
        )
        val text = String(body)
        if (code !in 200..299) throw LlmException("speech-to-text HTTP $code: ${text.take(200)}")
        return runCatching { Json.parseToJsonElement(text).jsonObject["text"]?.jsonPrimitive?.content }.getOrNull()?.trim()
            ?: throw LlmException("speech-to-text returned no text")
    }
}

/** OpenAI-style `/audio/speech` returning WAV: OpenAI (tts-1), or `opencell brain` (Piper). */
class OpenAiCompatibleTts(
    private val baseUrl: String,
    private val model: String,
    private val voice: String,
    private val apiKey: String?,
    private val http: SpeechHttp = UrlConnectionSpeechHttp,
) : TtsEngine {
    override val label = "$model/$voice (text-to-speech)"

    override suspend fun synthesize(text: String): Pcm = withContext(Dispatchers.IO) {
        val json = buildJsonObject {
            put("model", model)
            put("input", text)
            put("voice", voice)
            put("response_format", "wav")
        }.toString()
        val (code, body) = http.postJson(baseUrl.trimEnd('/') + "/audio/speech", authHeaders(apiKey), json)
        if (code !in 200..299) throw LlmException("text-to-speech HTTP $code: ${String(body).take(200)}")
        runCatching { Wav.decode(body) }.getOrElse { throw LlmException("text-to-speech returned invalid audio", it) }
    }
}
