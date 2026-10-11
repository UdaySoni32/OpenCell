package com.opencell.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.util.Log
import com.opencell.agent.AssistantNotifier
import com.opencell.ai.AiSettingsStore
import com.opencell.ai.SpeechConfig
import com.opencell.ai.SpeechKind
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
import com.opencell.telecom.CallManager
import com.opencell.telecom.CallState
import com.opencell.voice.shizuku.ShizukuCallAudio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

data class VoiceSession(val callId: String, val mode: CallAudioMode, val listenOnly: Boolean, val lines: List<TranscriptLine> = emptyList(), val status: String = "starting")

/** Starts and supervises AI voice sessions on SIM calls: "Let AI handle", auto pickup, AI-placed calls, listen-and-learn. */
class VoiceController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val ai: AiSettingsStore,
    private val calls: CallManager,
    private val executor: ActionExecutor,
    private val contacts: ContactsRepository,
    private val memory: MemoryDao,
    private val notifier: AssistantNotifier,
) {
    val shizuku = ShizukuCallAudio(context)
    private val androidTts by lazy { AndroidTts(context) }
    private val androidStt by lazy { AndroidStt(context) }

    private val _session = MutableStateFlow<VoiceSession?>(null)
    val session: StateFlow<VoiceSession?> = _session.asStateFlow()
    private var job: Job? = null

    /** Goals for calls the AI was asked to place, keyed by number, consumed when that call connects. */
    private data class PendingGoal(val goal: String, val atMillis: Long)
    private val pendingGoals = ConcurrentHashMap<String, PendingGoal>()

    fun capabilities() = AudioCapabilities(
        shizukuReady = shizuku.status() == ShizukuCallAudio.Status.READY,
        hasMicPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
    )

    fun rememberGoal(number: String, goal: String) {
        ActionPolicy.numberKey(number)?.let { pendingGoals[it] = PendingGoal(goal.take(500), System.currentTimeMillis()) }
    }

    fun forgetGoal(number: String) {
        ActionPolicy.numberKey(number)?.let { pendingGoals.remove(it) }
    }

    /** A goal only applies to a call that connects soon after the request; never to a later manual call. */
    private fun takeGoal(key: String): String? =
        pendingGoals.remove(key)?.takeIf { System.currentTimeMillis() - it.atMillis < GOAL_TTL_MS }?.goal

    /** Called on every call state change (from the container's call event loop). */
    fun onCallState(callId: String, number: String?, state: CallState, isIncoming: Boolean) {
        val key = number?.let(ActionPolicy::numberKey)
        when {
            state == CallState.RINGING && isIncoming -> scope.launch { maybeAutoPickup(callId, number) }
            state == CallState.ACTIVE && !isIncoming && key != null && pendingGoals.containsKey(key) ->
                takeGoal(key)?.let { goal -> handle(callId, goal) }
            state == CallState.ACTIVE && isIncoming && _session.value == null -> scope.launch { maybeListen(callId) }
        }
    }

    /** "Let AI handle" on an active call (goal = null means: answer on the user's behalf). */
    fun handle(callId: String, goal: String? = null): String? {
        if (_session.value != null) return "the AI is already on a call"
        val mode = CallAudioModes.forVoice(capabilities()) ?: return "allow the microphone or Shizuku first"
        _session.value = VoiceSession(callId, mode, listenOnly = false)
        job = scope.launch {
            try {
                runVoice(callId, mode, goal)
            } catch (e: CancellationException) {
                throw e // the user took the call back
            } catch (e: Exception) {
                Log.e(TAG, "voice session failed", e)
                notifier.info("AI couldn't handle the call", e.message.orEmpty().take(200))
            } finally {
                _session.value = null
            }
        }
        return null
    }

    fun stop() {
        job?.cancel()
        shizuku.stop()
        _session.value = null
    }

    private suspend fun runVoice(callId: String, mode: CallAudioMode, goal: String?) {
        val settings = ai.current()
        val llm = ai.buildLlm() ?: error("set up an AI provider first")
        val call = calls.activeCalls.value.firstOrNull { it.id == callId } ?: error("the call has ended")
        val number = call.number.orEmpty()
        val label = call.name ?: withContext(Dispatchers.IO) { contacts.nameFor(number) } ?: number.ifEmpty { "the caller" }
        val facts = memory.relevant(ActionPolicy.numberKey(number))
        val system = PromptBuilder.voiceCall(settings.userName, ai.currentInstructions(), facts, label, goal, VoiceAgent.END_MARKER)
        val opening = if (goal != null) null else greeting(settings.userName)

        calls.setSpeaker(true) // the far end hears the assistant through the speakerphone in every mode
        val raw = when (mode) {
            CallAudioMode.SHIZUKU -> StreamInput { shizuku.openStream(MediaRecorder.AudioSource.VOICE_DOWNLINK) }
            else -> MicInput()
        }
        val input = boundToCall(raw, callId)
        val agent = VoiceAgent(
            input = input,
            output = SpeakerOutput(),
            stt = stt(settings.stt),
            tts = tts(settings.tts),
            llm = llm,
            systemPrompt = system,
            opening = opening,
            onLine = { line -> _session.value = _session.value?.let { it.copy(lines = it.lines + line, status = "talking") } },
            // Speaker mode's mic hears our own voice; Shizuku's downlink feed only hears the caller.
            halfDuplex = mode == CallAudioMode.SPEAKER,
        )
        val transcript = agent.run()
        shizuku.stop()
        if (calls.activeCalls.value.any { it.id == callId }) executor.submit(ActionRequest(Actor.Ai("voice"), ActionType.HANGUP_CALL, callId))
        report(label, number, transcript, goal)
    }

    private suspend fun maybeAutoPickup(callId: String, number: String?) {
        val settings = ai.current()
        if (!settings.autoPickup || settings.primary == null || CallAudioModes.forVoice(capabilities()) == null) return
        delay(settings.autoPickupSeconds * 1000L)
        if (calls.activeCalls.value.none { it.id == callId && it.state == CallState.RINGING }) return
        executor.submit(ActionRequest(Actor.Ai("voice"), ActionType.ANSWER_CALL, callId))
        val active = withTimeoutOrNull(10_000) { calls.activeCalls.first { list -> list.any { it.id == callId && it.state == CallState.ACTIVE } } }
        if (active != null) handle(callId)
        else Log.w(TAG, "auto pickup: call $callId (${number ?: "unknown"}) did not connect")
    }

    /** Silently transcribes a call the user answered, then learns facts from it (opt-in, Shizuku only). */
    private suspend fun maybeListen(callId: String) {
        val settings = ai.current()
        if (!settings.learnFromCalls || settings.primary == null) return
        CallAudioModes.forListening(capabilities()) ?: return
        delay(1_500) // give "Let AI handle" / auto pickup a chance to claim the call first
        if (_session.value != null) return
        val call = calls.activeCalls.value.firstOrNull { it.id == callId } ?: return
        _session.value = VoiceSession(callId, CallAudioMode.SHIZUKU, listenOnly = true, status = "listening")
        job = scope.launch {
            val lines = mutableListOf<ThreadLine>()
            runCatching {
                val input = boundToCall(StreamInput { shizuku.openStream(MediaRecorder.AudioSource.VOICE_CALL) }, callId)
                val vad = EnergyVad()
                val engine = stt(settings.stt)
                input.frames().collect { frame ->
                    val e = vad.accept(frame)
                    if (e is EnergyVad.Event.Utterance && e.pcm.durationMs > 300) {
                        val text = withContext(Dispatchers.IO) { runCatching { engine.transcribe(e.pcm) }.getOrDefault("") }.trim()
                        if (text.isNotEmpty()) lines += ThreadLine(fromThem = true, text = text)
                    }
                }
            }.onFailure { Log.w(TAG, "listening stopped", it) }
            shizuku.stop()
            _session.value = null
            if (lines.isNotEmpty()) learn(call.name ?: call.number.orEmpty(), call.number, lines)
        }
    }

    private suspend fun report(label: String, number: String, transcript: List<TranscriptLine>, goal: String?) {
        val heard = transcript.filter { it.speaker == Speaker.THEM }.joinToString(" / ") { it.text }
        notifier.info(
            if (goal == null) "AI answered $label" else "AI call to $label finished",
            heard.ifEmpty { "The caller didn't say anything." }.take(400),
        )
        if (ai.current().learnFromCalls && transcript.isNotEmpty()) {
            learn(label, number, transcript.map { ThreadLine(it.speaker == Speaker.THEM, it.text) })
        }
    }

    private suspend fun learn(label: String, number: String?, lines: List<ThreadLine>) {
        val llm = ai.buildLlm() ?: return
        val key = number?.let(ActionPolicy::numberKey)
        val prompt = PromptBuilder.factExtraction(label, lines)
        val answer = runCatching { withContext(Dispatchers.IO) { llm.complete(prompt.system, prompt.messages, 300) } }.getOrNull() ?: return
        val known = memory.relevant(key, 200).map { it.text.lowercase() }.toSet()
        PromptBuilder.parseFacts(answer, label).filter { it.second.lowercase() !in known }.forEach { (aboutUser, text) ->
            memory.insert(MemoryFact(contactKey = if (aboutUser) null else key, text = text, source = "call with $label", createdAtMillis = System.currentTimeMillis()))
        }
    }

    /** The mic keeps working after a hang-up, so end the audio stream as soon as the call is gone. */
    private fun boundToCall(input: AudioInput, callId: String): AudioInput = object : AudioInput {
        override fun frames() = input.frames().takeWhile { calls.activeCalls.value.any { it.id == callId } }
    }

    private fun stt(c: SpeechConfig): SttEngine = when (c.kind) {
        SpeechKind.ON_DEVICE -> androidStt
        SpeechKind.OPENAI_COMPATIBLE -> OpenAiCompatibleStt(c.baseUrl, c.model, ai.speechKey("stt"))
    }

    private fun tts(c: SpeechConfig): TtsEngine = when (c.kind) {
        SpeechKind.ON_DEVICE -> androidTts
        SpeechKind.OPENAI_COMPATIBLE -> OpenAiCompatibleTts(c.baseUrl, c.model, c.voice.ifBlank { "alloy" }, ai.speechKey("tts"))
    }

    companion object {
        private const val TAG = "VoiceController"
        private const val GOAL_TTL_MS = 10 * 60_000L

        fun greeting(userName: String): String = if (userName.isBlank()) {
            "Hi, this is an AI assistant answering for this number. They can't come to the phone right now. Who's calling, please?"
        } else {
            "Hi, this is ${userName.trim()}'s AI assistant. ${userName.trim()} can't come to the phone right now. Who's calling, please?"
        }
    }
}
