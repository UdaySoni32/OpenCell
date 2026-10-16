package com.opencell.voice

import com.opencell.ai.LlmException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTest {

    @Test
    fun `wav round trip keeps samples and rate`() {
        val pcm = Pcm(shortArrayOf(0, 1000, -1000, Short.MAX_VALUE, Short.MIN_VALUE), 22_050)
        val back = Wav.decode(Wav.encode(pcm))
        assertArrayEquals(pcm.samples, back.samples)
        assertEquals(22_050, back.sampleRate)
    }

    @Test
    fun `wav decode handles streaming size and rejects junk`() {
        val bytes = Wav.encode(Pcm(ShortArray(100) { it.toShort() }))
        java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(40, -1) // unknown data size
        assertEquals(100, Wav.decode(bytes).samples.size)
        assertThrows(IllegalArgumentException::class.java) { Wav.decode("hello".toByteArray()) }
    }

    private class FakeHttp(private val code: Int, private val body: ByteArray) : SpeechHttp {
        var url = ""
        var fields = emptyMap<String, String>()
        var json = ""
        override fun postMultipart(url: String, headers: Map<String, String>, fields: Map<String, String>, fileField: String, fileName: String, file: ByteArray): Pair<Int, ByteArray> {
            this.url = url
            this.fields = fields
            assertTrue("uploads a wav", String(file, 0, 4) == "RIFF")
            return code to body
        }
        override fun postJson(url: String, headers: Map<String, String>, json: String): Pair<Int, ByteArray> {
            this.url = url
            this.json = json
            return code to body
        }
    }

    @Test
    fun `stt posts wav and reads text`() {
        val http = FakeHttp(200, """{"text":" hello there "}""".toByteArray())
        val text = OpenAiCompatibleStt("http://brain:8788/v1/", "whisper", null, http).transcribe(Pcm(ShortArray(1600)))
        assertEquals("hello there", text)
        assertEquals("http://brain:8788/v1/audio/transcriptions", http.url)
        assertEquals("whisper", http.fields["model"])
    }

    @Test
    fun `tts asks for wav and decodes it`() { runBlocking {
        val audio = Wav.encode(Pcm(ShortArray(480), 24_000))
        val http = FakeHttp(200, audio)
        val pcm = OpenAiCompatibleTts("https://api.openai.com/v1", "tts-1", "alloy", "k", http).synthesize("Hi")
        assertEquals(24_000, pcm.sampleRate)
        assertTrue(http.json.contains("\"response_format\":\"wav\""))
        assertThrows(LlmException::class.java) { runBlocking { OpenAiCompatibleTts("http://x", "m", "v", null, FakeHttp(500, ByteArray(0))).synthesize("Hi") } }
    }
    }

    @Test
    fun `greeting reads naturally with and without a name`() {
        assertEquals("Hi, this is Uday's AI assistant. Uday can't come to the phone right now. Who's calling, please?", VoiceController.greeting(" Uday "))
        assertTrue(VoiceController.greeting("").startsWith("Hi, this is an AI assistant answering for this number."))
    }

    @Test
    fun `mode selection prefers shizuku then speaker`() {
        assertEquals(CallAudioMode.SHIZUKU, CallAudioModes.forVoice(AudioCapabilities(shizukuReady = true, hasMicPermission = true)))
        assertEquals(CallAudioMode.SPEAKER, CallAudioModes.forVoice(AudioCapabilities(shizukuReady = false, hasMicPermission = true)))
        assertNull(CallAudioModes.forVoice(AudioCapabilities(shizukuReady = false, hasMicPermission = false)))
        assertNull("speaker mode can't silently transcribe", CallAudioModes.forListening(AudioCapabilities(false, true)))
        assertEquals(listOf(CallAudioMode.TEXT), CallAudioModes.available(AudioCapabilities(false, false)))
    }
}
