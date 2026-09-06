package com.example.opencell.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import com.example.opencell.OpenCellApplication
import com.example.opencell.data.repository.CallRepository
import com.example.opencell.domain.model.CallRecord
import com.example.opencell.domain.model.CallSession
import com.example.opencell.domain.model.CallState
import com.example.opencell.domain.model.CallType
import com.example.opencell.gateway.event.EventEngine
import com.example.opencell.ui.incall.InCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class CallEngine(
    private val telecomAdapter: TelecomAdapter,
    private val callRepository: CallRepository,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.Default + Job())
) {

    private val _activeCallSession = MutableStateFlow<CallSession?>(null)
    val activeCallSession: StateFlow<CallSession?> = _activeCallSession.asStateFlow()

    private val _isModemAvailable = MutableStateFlow(telecomAdapter.isTelephonyCapable())
    val isModemAvailable: StateFlow<Boolean> = _isModemAvailable.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var activeSystemCall: Call? = null
    private var currentInCallService: InCallService? = null
    private var durationJob: Job? = null

    init {
        instance = this
    }

    fun refreshModemStatus() {
        _isModemAvailable.value = telecomAdapter.isTelephonyCapable()
    }

    fun initiateCall(phoneNumber: String, contactName: String? = null, allowSimulationFallback: Boolean = true) {
        val sanitizedNumber = phoneNumber.trim()
        if (sanitizedNumber.isBlank()) {
            _statusMessage.value = "Cannot place call: Empty phone number"
            return
        }

        refreshModemStatus()
        val hasPermission = telecomAdapter.hasCallPermission()
        val hasModem = telecomAdapter.isTelephonyCapable()

        val session = CallSession(
            phoneNumber = sanitizedNumber,
            contactName = contactName,
            state = CallState.DIALING,
            isIncoming = false,
            isSimulated = false
        )
        _activeCallSession.value = session

        launchInCallUi()

        if (hasModem && hasPermission) {
            val success = telecomAdapter.placeCall(sanitizedNumber)
            if (success) {
                _statusMessage.value = "Placing system call to $sanitizedNumber"
                EventEngine.instance.emitCallCreated(
                    callId = session.phoneNumber,
                    phoneNumber = session.phoneNumber,
                    type = "OUTGOING"
                )
                return
            }
        }

        if (!allowSimulationFallback) {
            val error = when {
                !hasModem -> "Hardware modem unavailable on device"
                !hasPermission -> "Call permission (CALL_PHONE) not granted"
                else -> "Failed to initiate telecom call"
            }
            _statusMessage.value = error
            persistEndedCall(
                CallSession(
                    phoneNumber = sanitizedNumber,
                    contactName = contactName,
                    state = CallState.FAILED,
                    isIncoming = false,
                    isSimulated = false,
                    disconnectReason = error
                )
            )
            return
        }

        // Simulation Fallback for emulators/dev mode when telecom stack is unavailable
        val simSession = session.copy(isSimulated = true)
        _activeCallSession.value = simSession
        _statusMessage.value = "Simulated call started for $sanitizedNumber (no hardware modem)"

        externalScope.launch {
            delay(1500)
            val current = _activeCallSession.value
            if (current != null && current.state == CallState.DIALING) {
                val activeSession = current.copy(
                    state = CallState.ACTIVE,
                    connectTimeMillis = System.currentTimeMillis()
                )
                _activeCallSession.value = activeSession
                startDurationTimer()
            }
        }
    }

    /**
     * Brings up OpenCell's own InCallActivity whenever a call is initiated so the
     * call is never handed off to the OEM/default phone app's UI. No-op when the
     * application instance is unavailable (e.g. JVM unit tests).
     */
    private fun launchInCallUi() {
        val appContext = OpenCellApplication.instanceOrNull ?: return
        try {
            val inCallIntent = Intent(appContext, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            appContext.startActivity(inCallIntent)
        } catch (_: Exception) {
            // Never let UI launching break the underlying call initiation.
        }
    }

    fun answerCall() {
        val session = _activeCallSession.value ?: return
        if (activeSystemCall != null) {
            telecomAdapter.answerCall(activeSystemCall)
        } else {
            val activeSession = session.copy(
                state = CallState.ACTIVE,
                connectTimeMillis = System.currentTimeMillis()
            )
            _activeCallSession.value = activeSession
            startDurationTimer()
            _statusMessage.value = "Call answered"
            EventEngine.instance.emitCallStateChanged(
                callId = activeSession.phoneNumber,
                phoneNumber = activeSession.phoneNumber,
                state = "ACTIVE"
            )
        }
    }

    fun rejectCall() {
        val session = _activeCallSession.value ?: return
        if (activeSystemCall != null) {
            telecomAdapter.rejectCall(activeSystemCall)
        } else {
            val endedSession = session.copy(
                state = CallState.REJECTED,
                disconnectReason = "Rejected by user"
            )
            _activeCallSession.value = endedSession
            persistEndedCall(endedSession)
            stopDurationTimer()
            _activeCallSession.value = null
            _statusMessage.value = "Call rejected"
            EventEngine.instance.emitCallStateChanged(
                callId = endedSession.phoneNumber,
                phoneNumber = endedSession.phoneNumber,
                state = "REJECTED"
            )
        }
    }

    fun hangupCall() {
        val session = _activeCallSession.value ?: return
        if (activeSystemCall != null) {
            telecomAdapter.hangupCall(activeSystemCall)
        } else {
            val endedSession = session.copy(
                state = CallState.ENDED,
                disconnectReason = "Hung up by user"
            )
            _activeCallSession.value = endedSession
            persistEndedCall(endedSession)
            stopDurationTimer()
            _activeCallSession.value = null
            _statusMessage.value = "Call ended"
            com.example.opencell.gateway.event.EventEngine.instance.emitCallStateChanged(
                callId = endedSession.phoneNumber,
                phoneNumber = endedSession.phoneNumber,
                state = "ENDED"
            )
        }
    }

    fun toggleMute() {
        val current = _activeCallSession.value ?: return
        val newMute = !current.isMuted
        telecomAdapter.setMute(newMute, currentInCallService)
        _activeCallSession.value = current.copy(isMuted = newMute)
    }

    fun toggleSpeaker() {
        val current = _activeCallSession.value ?: return
        val newSpeaker = !current.isSpeakerOn
        telecomAdapter.setSpeaker(newSpeaker, currentInCallService)
        _activeCallSession.value = current.copy(isSpeakerOn = newSpeaker)
    }

    fun toggleHold() {
        val current = _activeCallSession.value ?: return
        val newHold = !current.isOnHold
        if (activeSystemCall != null) {
            telecomAdapter.setHold(activeSystemCall, newHold)
        }
        val newState = if (newHold) CallState.HELD else CallState.ACTIVE
        _activeCallSession.value = current.copy(isOnHold = newHold, state = newState)
        if (newState == CallState.ACTIVE || newState == CallState.HELD) {
            startDurationTimer()
        }
    }

    fun sendDtmf(digit: Char) {
        activeSystemCall?.let { telecomAdapter.playDtmfTone(digit, it) }
        _statusMessage.value = "DTMF Sent: $digit"
    }

    fun stopDtmf() {
        activeSystemCall?.let { telecomAdapter.stopDtmfTone(it) }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    // Callbacks invoked by OpenCellInCallService
    internal fun onSystemCallAdded(call: Call, service: InCallService) {
        activeSystemCall = call
        currentInCallService = service
        val handle = call.details?.handle?.schemeSpecificPart ?: "Unknown"
        val state = mapSystemCallState(call.state)

        val session = CallSession(
            phoneNumber = handle,
            contactName = _activeCallSession.value?.contactName,
            state = state,
            isIncoming = call.state == Call.STATE_RINGING,
            isSimulated = false
        )
        _activeCallSession.value = session

        if (state == CallState.ACTIVE) {
            startDurationTimer()
        }
    }

    internal fun onSystemCallStateChanged(call: Call, state: Int) {
        val mappedState = mapSystemCallState(state)
        val current = _activeCallSession.value ?: return

        val updated = current.copy(
            state = mappedState,
            connectTimeMillis = if (mappedState == CallState.ACTIVE && current.connectTimeMillis == null) System.currentTimeMillis() else current.connectTimeMillis
        )
        _activeCallSession.value = updated

        if (mappedState == CallState.ACTIVE) {
            startDurationTimer()
        } else if (mappedState == CallState.ENDED || mappedState == CallState.FAILED || mappedState == CallState.REJECTED) {
            persistEndedCall(updated)
            stopDurationTimer()
            _activeCallSession.value = null
            activeSystemCall = null
        }
    }

    internal fun onSystemCallRemoved(call: Call) {
        if (activeSystemCall == call) {
            val session = _activeCallSession.value
            if (session != null && session.state != CallState.ENDED) {
                val ended = session.copy(state = CallState.ENDED)
                persistEndedCall(ended)
            }
            stopDurationTimer()
            _activeCallSession.value = null
            activeSystemCall = null
            currentInCallService = null
        }
    }

    private fun mapSystemCallState(systemState: Int): CallState {
        return when (systemState) {
            Call.STATE_NEW, Call.STATE_SELECT_PHONE_ACCOUNT -> CallState.NEW
            Call.STATE_CONNECTING, Call.STATE_DIALING -> CallState.DIALING
            Call.STATE_RINGING -> CallState.RINGING
            Call.STATE_ACTIVE -> CallState.ACTIVE
            Call.STATE_HOLDING -> CallState.HELD
            Call.STATE_DISCONNECTING -> CallState.ENDING
            Call.STATE_DISCONNECTED -> CallState.ENDED
            else -> CallState.FAILED
        }
    }

    private fun startDurationTimer() {
        stopDurationTimer()
        durationJob = externalScope.launch {
            while (_activeCallSession.value?.state == CallState.ACTIVE || _activeCallSession.value?.state == CallState.HELD) {
                delay(1000)
                _activeCallSession.value = _activeCallSession.value?.let { session ->
                    val connectTime = session.connectTimeMillis ?: System.currentTimeMillis()
                    val seconds = ((System.currentTimeMillis() - connectTime) / 1000).toInt()
                    session.copy(durationSeconds = seconds)
                }
            }
        }
    }

    private fun stopDurationTimer() {
        durationJob?.cancel()
        durationJob = null
    }

    private fun persistEndedCall(session: CallSession) {
        externalScope.launch {
            val callType = when {
                session.state == CallState.MISSED -> CallType.MISSED
                session.isIncoming -> CallType.INCOMING
                else -> CallType.OUTGOING
            }
            val record = CallRecord(
                phoneNumber = session.phoneNumber,
                contactName = session.contactName,
                callType = callType,
                timestamp = session.startTimeMillis,
                durationSeconds = session.durationSeconds
            )
            callRepository.insertCall(record)
        }
    }

    companion object {
        @Volatile
        private var instanceRef: WeakReference<CallEngine>? = null

        var instance: CallEngine?
            get() = instanceRef?.get()
            set(value) {
                instanceRef = if (value != null) WeakReference(value) else null
            }
    }
}
