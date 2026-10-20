package com.opencell.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

enum class CallState { DIALING, RINGING, CONNECTING, ACTIVE, HOLDING, DISCONNECTED }

data class ActiveCall(
    val id: String,
    val number: String?,
    val name: String?,
    val state: CallState,
    val isIncoming: Boolean,
    val connectedAtMillis: Long?,
) {
    val displayName: String get() = name ?: number ?: "Unknown"
}

data class AudioUi(val isMuted: Boolean = false, val isSpeaker: Boolean = false)

/** Single source of truth for SIM calls handled by our InCallService. */
class CallManager {
    private val calls = ConcurrentHashMap<String, Call>()
    private val _activeCalls = MutableStateFlow<List<ActiveCall>>(emptyList())
    val activeCalls: StateFlow<List<ActiveCall>> = _activeCalls.asStateFlow()

    private val _audio = MutableStateFlow(AudioUi())
    val audio: StateFlow<AudioUi> = _audio.asStateFlow()

    @Volatile private var service: InCallService? = null
    private var nextId = 0

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = publish()
        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
    }

    fun attach(service: InCallService) {
        this.service = service
    }

    fun detach() {
        service = null
    }

    @Synchronized
    fun add(call: Call): String {
        val id = "call-${nextId++}"
        calls[id] = call
        call.registerCallback(callback)
        publish()
        return id
    }

    fun remove(call: Call) {
        call.unregisterCallback(callback)
        calls.entries.removeIf { it.value == call }
        publish()
    }

    fun answer(id: String) = calls[id]?.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun reject(id: String) = calls[id]?.reject(false, null)
    fun hangup(id: String) = calls[id]?.disconnect()
    fun playDtmf(id: String, digit: Char) {
        calls[id]?.apply { playDtmfTone(digit); stopDtmfTone() }
    }

    fun toggleHold(id: String) {
        val call = calls[id] ?: return
        if (stateOf(call) == Call.STATE_HOLDING) call.unhold() else call.hold()
    }

    fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    @Suppress("DEPRECATION") // setAudioRoute is the only route API that works on API 30
    fun setSpeaker(on: Boolean) {
        service?.setAudioRoute(if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE)
    }

    fun onAudioStateChanged(state: CallAudioState?) {
        state ?: return
        _audio.value = AudioUi(isMuted = state.isMuted, isSpeaker = state.route == CallAudioState.ROUTE_SPEAKER)
    }

    fun firstIdWhere(predicate: (ActiveCall) -> Boolean): String? = activeCalls.value.firstOrNull(predicate)?.id

    /** The token travels with this specific outgoing call, including same-number calls. */
    fun voiceGoalToken(id: String): String? {
        val extras = calls[id]?.details?.intentExtras ?: return null
        return extras.getBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS)?.getString(CallPlacer.VOICE_GOAL_TOKEN)
            ?: extras.getString(CallPlacer.VOICE_GOAL_TOKEN)
    }

    private fun publish() {
        _activeCalls.update { calls.map { (id, call) -> call.toUi(id) }.sortedBy { it.id.removePrefix("call-").toInt() } }
    }

    private fun Call.toUi(id: String): ActiveCall {
        val d = details
        return ActiveCall(
            id = id,
            number = d.handle?.schemeSpecificPart,
            name = d.contactDisplayName ?: d.callerDisplayName?.takeIf { it.isNotBlank() },
            state = when (stateOf(this)) {
                Call.STATE_RINGING -> CallState.RINGING
                Call.STATE_DIALING, Call.STATE_PULLING_CALL -> CallState.DIALING
                Call.STATE_CONNECTING, Call.STATE_NEW, Call.STATE_SELECT_PHONE_ACCOUNT -> CallState.CONNECTING
                Call.STATE_ACTIVE -> CallState.ACTIVE
                Call.STATE_HOLDING -> CallState.HOLDING
                else -> CallState.DISCONNECTED
            },
            isIncoming = d.callDirection == Call.Details.DIRECTION_INCOMING,
            connectedAtMillis = d.connectTimeMillis.takeIf { it > 0 },
        )
    }

    companion object {
        @Suppress("DEPRECATION")
        fun stateOf(call: Call): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) call.details.state else call.state

        fun isSelfManaged(call: Call): Boolean = call.details.hasProperty(Call.Details.PROPERTY_SELF_MANAGED)
    }
}
