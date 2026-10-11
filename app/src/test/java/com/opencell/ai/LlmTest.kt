package com.opencell.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmTest {

    private class Recorder(private val result: HttpResult) : HttpPost {
        var url = ""
        var headers = emptyMap<String, String>()
        var body = ""
        override fun post(url: String, headers: Map<String, String>, jsonBody: String): HttpResult {
            this.url = url
            this.headers = headers
            this.body = jsonBody
            return result
        }
    }

    private val history = listOf(ChatMessage(Role.USER, "hi"))

    @Test
    fun `openai compatible request and response`() {
        val http = Recorder(HttpResult(200, """{"choices":[{"message":{"role":"assistant","content":" Hello! "}}]}"""))
        val out = OpenAiCompatibleLlm("http://10.0.2.2:11434/v1/", "gemma3:4b", null, http).complete("sys", history)
        assertEquals("Hello!", out)
        assertEquals("http://10.0.2.2:11434/v1/chat/completions", http.url)
        assertTrue("no auth header without key", http.headers.isEmpty())
        assertTrue(http.body.contains("\"role\":\"system\",\"content\":\"sys\""))
        assertTrue(http.body.contains("\"model\":\"gemma3:4b\""))
    }

    @Test
    fun `openai key goes in bearer header`() {
        val http = Recorder(HttpResult(200, """{"choices":[{"message":{"content":"x"}}]}"""))
        OpenAiCompatibleLlm("https://api.openai.com/v1", "m", "sk-test", http).complete("s", history)
        assertEquals("Bearer sk-test", http.headers["Authorization"])
    }

    @Test
    fun `anthropic request and response`() {
        val http = Recorder(HttpResult(200, """{"content":[{"type":"text","text":"Hi "},{"type":"text","text":"there"}]}"""))
        val out = AnthropicLlm("key", "claude-haiku-5-5", http).complete("sys", history)
        assertEquals("Hi there", out)
        assertEquals("https://api.anthropic.com/v1/messages", http.url)
        assertEquals("key", http.headers["x-api-key"])
        assertTrue(http.body.contains("\"system\":\"sys\""))
    }

    @Test
    fun `http errors and bad shapes become LlmException`() {
        assertThrows(LlmException::class.java) { OpenAiCompatibleLlm("http://x", "m", null, Recorder(HttpResult(401, "nope"))).complete("s", history) }
        assertThrows(LlmException::class.java) { OpenAiCompatibleLlm("http://x", "m", null, Recorder(HttpResult(200, "{}"))).complete("s", history) }
        assertThrows(LlmException::class.java) { AnthropicLlm("k", "m", Recorder(HttpResult(200, "not json"))).complete("s", history) }
    }

    @Test
    fun `fallback uses the next provider and reports all errors`() {
        val bad = OpenAiCompatibleLlm("http://down", "m", null) { _, _, _ -> throw java.net.ConnectException("refused") }
        val good = OpenAiCompatibleLlm("http://up", "m", null, Recorder(HttpResult(200, """{"choices":[{"message":{"content":"ok"}}]}""")))
        assertEquals("ok", FallbackLlm(listOf(bad, good)).complete("s", history))
        val e = assertThrows(LlmException::class.java) { FallbackLlm(listOf(bad)).complete("s", history) }
        assertTrue(e.message!!.contains("refused"))
        assertThrows(LlmException::class.java) { FallbackLlm(emptyList()).complete("s", history) }
    }
}
