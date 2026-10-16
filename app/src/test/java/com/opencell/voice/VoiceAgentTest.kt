package com.opencell.voice

import com.opencell.ai.ChatMessage
import com.opencell.ai.LlmException
import com.opencell.ai.LlmProvider
import com.opencell.ai.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun loud() = ShortArray(Pcm.FRAME_SAMPLES) { if (it % 2 == 0) 4000 else -4000 }
private fun quiet() = ShortArray(Pcm.FRAME_SAMPLES)

/** Plays a script of (loud ms, quiet ms) segments in real (virtual) time. */
private class ScriptedInput(private val segments: List<Pair<Int, Int>>) : AudioInput {
    override fun frames(): Flow<ShortArray> = flow {
        for ((speechMs, silenceMs) in segments) {
            repeat(speechMs / Pcm.FRAME_MS) { emit(loud()); delay(Pcm.FRAME_MS.toLong()) }
            repeat(silenceMs / Pcm.FRAME_MS) { emit(quiet()); delay(Pcm.FRAME_MS.toLong()) }
        }
    }
}

private class RecordingOutput(private val playMs: Long = 500) : AudioOutput {
    val played = mutableListOf<Int>()
    var interrupted = 0
    override suspend fun play(pcm: Pcm) {
        try {
            delay(playMs)
            played += pcm.samples.size
        } catch (e: CancellationException) {
            interrupted++
            throw e
        }
    }
}

private class ScriptedStt(vararg texts: String) : SttEngine {
    private val queue = ArrayDeque(texts.toList())
    override val label = "fake-stt"
    override fun transcribe(pcm: Pcm) = queue.removeFirstOrNull() ?: ""
}

private object LengthTts : TtsEngine {
    override val label = "fake-tts"
    override suspend fun synthesize(text: String) = Pcm(ShortArray(text.length))
}

private class ScriptedLlm(vararg replies: String) : LlmProvider {
    private val queue = ArrayDeque(replies.toList())
    val seen = mutableListOf<List<ChatMessage>>()
    override val label = "fake-llm"
    override fun complete(system: String, messages: List<ChatMessage>, maxTokens: Int): String {
        seen += messages
        return queue.removeFirstOrNull() ?: throw LlmException("out of replies")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceAgentTest {

    @Test
    fun `vad finds one utterance per spoken segment and ignores silence`() {
        val vad = EnergyVad(endSilenceMs = 200)
        val events = (List(10) { quiet() } + List(25) { loud() } + List(15) { quiet() }).mapNotNull { vad.accept(it) }
        assertEquals(EnergyVad.Event.SpeechStarted, events[0])
        val utterance = events[1] as EnergyVad.Event.Utterance
        assertTrue("about 500ms of speech", utterance.pcm.durationMs in 480..800)
        assertEquals(2, events.size)
    }

    @Test
    fun `conversation turns and END marker finish the call`() = runTest {
        val output = RecordingOutput(playMs = 100)
        val llm = ScriptedLlm("Sure, I'll tell Uday you called.", "Goodbye! [END]")
        val agent = VoiceAgent(
            input = ScriptedInput(listOf(0 to 1500, 1000 to 1000, 800 to 1000, 0 to 20_000)),
            output = output,
            stt = ScriptedStt("Hi, is Uday there?", "Okay thanks, bye"),
            tts = LengthTts,
            llm = llm,
            systemPrompt = "sys",
            opening = "Hello, this is Uday's assistant.",
            io = StandardTestDispatcher(testScheduler),
        )
        val transcript = agent.run()
        assertEquals(
            listOf(Speaker.ASSISTANT, Speaker.THEM, Speaker.ASSISTANT, Speaker.THEM, Speaker.ASSISTANT),
            transcript.map { it.speaker },
        )
        assertEquals("Goodbye!", transcript.last().text)
        assertEquals(3, output.played.size)
        assertEquals("history starts with a user turn", Role.USER, llm.seen.first().first().role)
    }

    @Test
    fun `caller talking over the assistant interrupts playback`() = runTest {
        val output = RecordingOutput(playMs = 3_000)
        val agent = VoiceAgent(
            input = ScriptedInput(listOf(0 to 500, 800 to 1000)),
            output = output,
            stt = ScriptedStt(""),
            tts = LengthTts,
            llm = ScriptedLlm(),
            systemPrompt = "sys",
            opening = "A very long greeting that takes a while to say",
            io = StandardTestDispatcher(testScheduler),
        )
        agent.run()
        assertEquals(1, output.interrupted)
    }

    @Test
    fun `half duplex ignores audio while speaking so it never interrupts itself`() = runTest {
        val output = RecordingOutput(playMs = 3_000)
        val agent = VoiceAgent(
            input = ScriptedInput(listOf(0 to 500, 800 to 4_000)),
            output = output,
            stt = ScriptedStt("should never be heard"),
            tts = LengthTts,
            llm = ScriptedLlm(),
            systemPrompt = "sys",
            opening = "A very long greeting that takes a while to say",
            io = StandardTestDispatcher(testScheduler),
            halfDuplex = true,
        )
        val transcript = agent.run()
        assertEquals(0, output.interrupted)
        assertEquals(listOf(Speaker.ASSISTANT), transcript.map { it.speaker })
    }

    @Test
    fun `call ends when audio stops even if the model keeps failing`() = runTest {
        val agent = VoiceAgent(
            input = ScriptedInput(listOf(600 to 900, 600 to 900, 600 to 900, 600 to 900)),
            output = RecordingOutput(),
            stt = ScriptedStt("a", "b", "c", "d"),
            tts = LengthTts,
            llm = ScriptedLlm(),
            systemPrompt = "sys",
            opening = null,
            io = StandardTestDispatcher(testScheduler),
        )
        val transcript = agent.run()
        assertNotNull(transcript)
        assertEquals("stops after 3 failed turns", 3, transcript.count { it.speaker == Speaker.THEM })
    }
}
