package com.opencell.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.opencell.ai.AiSettingsStore
import com.opencell.ai.SpeechKind
import com.opencell.assistant.NO_PROVIDER_MESSAGE
import com.opencell.memory.PromptBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LocalVoiceCapabilities(
    val hasMicPermission: Boolean,
    val hasSpeechRecognizer: Boolean,
)

enum class LocalVoiceStatus(val label: String, val active: Boolean) {
    IDLE("Ready", false),
    STARTING("Starting voice conversation…", true),
    LISTENING("Listening…", true),
    SPEAKING("Speaking…", true),
    ERROR("Voice unavailable", false),
}

data class LocalVoiceState(
    val status: LocalVoiceStatus = LocalVoiceStatus.IDLE,
    val lines: List<TranscriptLine> = emptyList(),
    val error: String? = null,
)

/** Owns voice conversations that use this device's microphone and speaker, without Telecom. */
class LocalVoiceController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val ai: AiSettingsStore,
) {
    private val _state = MutableStateFlow(LocalVoiceState())
    val state: StateFlow<LocalVoiceState> = _state.asStateFlow()

    private var job: Job? = null
    private var generation = 0L
    private var androidTts: AndroidTts? = null

    fun capabilities(): LocalVoiceCapabilities = LocalVoiceCapabilities(
        hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        hasSpeechRecognizer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && SpeechRecognizer.isOnDeviceRecognitionAvailable(context),
    )

    /** Returns false when a session is already running or the microphone is not allowed. */
    fun start(goal: String?): Boolean {
        if (job?.isCompleted == false || _state.value.status.active) return false
        if (!capabilities().hasMicPermission) {
            showError("Microphone permission is required for a voice conversation.")
            return false
        }
        val token = ++generation
        _state.value = LocalVoiceState(status = LocalVoiceStatus.STARTING)
        job = scope.launch {
            var failed = false
            try {
                runSession(goal?.trim()?.takeIf { it.isNotEmpty() }, token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
                if (generation == token) _state.value = LocalVoiceState(LocalVoiceStatus.ERROR, _state.value.lines, voiceError(e))
            } finally {
                androidTts?.shutdown()
                androidTts = null
                if (generation == token) {
                    job = null
                    if (!failed && _state.value.status.active) {
                        _state.value = _state.value.copy(status = LocalVoiceStatus.IDLE)
                    }
                }
            }
        }
        return true
    }

    fun stop() {
        generation++
        job?.cancel()
        // Do not restart until cancelled speech/capture work has unwound.
        androidTts?.shutdown()
        androidTts = null
        if (_state.value.status.active) _state.value = _state.value.copy(status = LocalVoiceStatus.IDLE)
    }

    fun showError(message: String) {
        if (_state.value.status.active) return
        _state.value = _state.value.copy(status = LocalVoiceStatus.ERROR, error = message)
    }

    /** Stops audio promptly when the owning UI is disposed. */
    fun dispose() {
        stop()
        androidTts?.shutdown()
        androidTts = null
    }

    private suspend fun runSession(goal: String?, token: Long) {
        val settings = ai.current()
        val llm = ai.buildLlm() ?: error(NO_PROVIDER_MESSAGE)
        if (settings.stt.kind == SpeechKind.ON_DEVICE && !capabilities().hasSpeechRecognizer) {
            error("On-device speech-to-text is unavailable on this phone. Choose a server speech-to-text provider in Settings.")
        }
        val system = PromptBuilder.localVoice(settings.userName, ai.currentInstructions(), goal)
        val stt = when (settings.stt.kind) {
            SpeechKind.ON_DEVICE -> AndroidStt(context)
            SpeechKind.OPENAI_COMPATIBLE -> OpenAiCompatibleStt(settings.stt.baseUrl, settings.stt.model, ai.speechKey("stt"))
        }
        val tts = when (settings.tts.kind) {
            SpeechKind.ON_DEVICE -> AndroidTts(context).also { androidTts = it }
            SpeechKind.OPENAI_COMPATIBLE -> OpenAiCompatibleTts(settings.tts.baseUrl, settings.tts.model, settings.tts.voice.ifBlank { "alloy" }, ai.speechKey("tts"))
        }
        val rawOutput = SpeakerOutput()
        val output = object : AudioOutput {
            override suspend fun play(pcm: Pcm) {
                if (generation == token) _state.value = _state.value.copy(status = LocalVoiceStatus.SPEAKING)
                try {
                    rawOutput.play(pcm)
                } finally {
                    if (generation == token && _state.value.status.active) _state.value = _state.value.copy(status = LocalVoiceStatus.LISTENING)
                }
            }
        }
        _state.value = _state.value.copy(status = LocalVoiceStatus.LISTENING, error = null)
        VoiceAgent(
            input = MicInput(),
            output = output,
            stt = stt,
            tts = tts,
            llm = llm,
            systemPrompt = system,
            opening = "Hi! How can I help?",
            onLine = { line ->
                if (generation == token) {
                    _state.value = _state.value.copy(
                        status = if (line.speaker == Speaker.ASSISTANT) LocalVoiceStatus.SPEAKING else LocalVoiceStatus.LISTENING,
                        lines = _state.value.lines + line,
                        error = null,
                    )
                }
            },
            onError = { error ->
                if (generation == token && _state.value.status.active) _state.value = _state.value.copy(error = voiceError(error))
            },
            // The phone speaker can be picked up by the microphone; wait for the user between turns.
            halfDuplex = true,
        ).run()
    }

    private fun voiceError(error: Throwable): String {
        val detail = error.message?.trim().orEmpty()
        return if (detail.isBlank()) "Voice conversation stopped because speech was unavailable." else "Voice conversation unavailable: ${detail.take(240)}"
    }
}
