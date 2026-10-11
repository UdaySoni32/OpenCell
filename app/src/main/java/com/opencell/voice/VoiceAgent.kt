package com.opencell.voice

import com.opencell.ai.ChatMessage
import com.opencell.ai.LlmProvider
import com.opencell.ai.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class TranscriptLine(val speaker: Speaker, val text: String)

enum class Speaker { THEM, ASSISTANT }

/**
 * One AI-handled phone conversation: listen (VAD) → transcribe → LLM → speak, with barge-in.
 * Ends when the call audio stops, the model says [END_MARKER], [maxDurationMs] passes, or turns keep failing.
 */
class VoiceAgent(
    private val input: AudioInput,
    private val output: AudioOutput,
    private val stt: SttEngine,
    private val tts: TtsEngine,
    private val llm: LlmProvider,
    private val systemPrompt: String,
    private val opening: String?,
    private val onLine: (TranscriptLine) -> Unit = {},
    private val maxDurationMs: Long = 10 * 60_000L,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val vad: EnergyVad = EnergyVad(),
    /**
     * Ignore input while speaking (no barge-in). Needed when the input also hears our own voice,
     * i.e. speaker mode where the mic picks up the speakerphone.
     */
    private val halfDuplex: Boolean = false,
) {
    private val history = mutableListOf(ChatMessage(Role.USER, "(The call is connected.)"))
    private val transcript = mutableListOf<TranscriptLine>()

    suspend fun run(): List<TranscriptLine> = coroutineScope {
        val utterances = Channel<Pcm>(capacity = 4)
        var speaking: Job? = null
        val reader = launch {
            input.frames().collect { frame ->
                if (halfDuplex && speaking?.isActive == true) {
                    vad.reset()
                    return@collect
                }
                when (val e = vad.accept(frame)) {
                    EnergyVad.Event.SpeechStarted -> speaking?.cancel() // barge-in: stop talking when they talk
                    is EnergyVad.Event.Utterance -> utterances.send(e.pcm)
                    null -> Unit
                }
            }
            utterances.close() // call audio ended
        }
        opening?.let {
            add(Speaker.ASSISTANT, it)
            speaking = launch { say(it) }
        }
        var failures = 0
        withTimeoutOrNull(maxDurationMs) {
            for (pcm in utterances) {
                if (pcm.durationMs < MIN_UTTERANCE_MS) continue
                val turn = runCatching { takeTurn(pcm) }.onFailure { if (it is CancellationException) throw it }
                val result = turn.getOrNull()
                if (result == null) {
                    if (turn.isFailure && ++failures >= MAX_FAILURES) break
                    continue
                }
                failures = 0
                if (result.text.isNotEmpty()) {
                    speaking?.join()
                    speaking = launch { say(result.text) }
                }
                if (result.end) {
                    speaking?.join()
                    break
                }
            }
        }
        reader.cancel()
        speaking?.cancel()
        transcript.toList()
    }

    private data class Turn(val text: String, val end: Boolean)

    /** Null when nothing intelligible was said. */
    private suspend fun takeTurn(pcm: Pcm): Turn? {
        val heard = withContext(io) { stt.transcribe(pcm) }.trim()
        if (heard.isEmpty()) return null
        add(Speaker.THEM, heard)
        val raw = withContext(io) { llm.complete(systemPrompt, history.toList(), MAX_REPLY_TOKENS) }
        val end = raw.contains(END_MARKER)
        val text = raw.replace(END_MARKER, "").trim()
        if (text.isNotEmpty()) add(Speaker.ASSISTANT, text)
        return Turn(text, end)
    }

    private suspend fun say(text: String) {
        output.play(withContext(io) { tts.synthesize(text) })
    }

    private fun add(speaker: Speaker, text: String) {
        val line = TranscriptLine(speaker, text)
        transcript += line
        history += ChatMessage(if (speaker == Speaker.THEM) Role.USER else Role.ASSISTANT, text)
        onLine(line)
    }

    companion object {
        const val END_MARKER = "[END]"
        private const val MIN_UTTERANCE_MS = 300
        private const val MAX_FAILURES = 3
        private const val MAX_REPLY_TOKENS = 200
    }
}
