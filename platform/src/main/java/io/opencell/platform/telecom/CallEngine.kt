package io.opencell.platform.telecom

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.opencell.core.crypto.CryptoUtils
import io.opencell.core.database.dao.AuditLogDao
import io.opencell.core.database.dao.CallDao
import io.opencell.core.database.entity.AuditLogEntity
import io.opencell.core.database.entity.CallEntity
import io.opencell.core.database.entity.CallEventEntity
import io.opencell.core.model.*
import io.opencell.platform.events.EventEngine
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified Call Engine.
 * Human interaction and API interaction control the same call state.
 */
@Singleton
class CallEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callDao: CallDao,
    private val auditLogDao: AuditLogDao,
    private val eventEngine: EventEngine
) {
    companion object {
        private const val TAG = "CallEngine"
    }

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val _activeCalls = MutableStateFlow<List<io.opencell.core.model.Call>>(emptyList())
    val activeCalls: StateFlow<List<io.opencell.core.model.Call>> = _activeCalls.asStateFlow()

    private val _currentCall = MutableStateFlow<io.opencell.core.model.Call?>(null)
    val currentCall: StateFlow<io.opencell.core.model.Call?> = _currentCall.asStateFlow()

    /**
     * Place an outgoing call.
     *
     * If OpenCell is the default dialer, launches ACTION_CALL so the Telecom framework
     * routes through [OpenCellConnectionService]. Otherwise, falls back to ACTION_DIAL
     * (opens the default dialer with the number pre-filled).
     *
     * The call record is created in the database first so the API and UI can track it.
     * The actual telephony connection is managed by the system Telecom framework.
     */
    suspend fun makeCall(
        phoneNumber: String,
        subscriptionId: Int? = null,
        deviceId: String
    ): Result<io.opencell.core.model.Call> {
        val callId = CryptoUtils.generateId("call")
        val call = io.opencell.core.model.Call(
            id = callId,
            deviceId = deviceId,
            subscriptionId = subscriptionId ?: 0,
            direction = CallDirection.OUTBOUND,
            from = "",
            to = phoneNumber,
            state = CallState.CREATED,
            startedAt = System.currentTimeMillis()
        )

        return try {
            callDao.upsertCall(call.toCallEntity())
            eventEngine.emit(EventNames.CALL_CREATED, deviceId, mapOf(
                "call_id" to JsonPrimitive(callId),
                "direction" to JsonPrimitive("OUTBOUND"),
                "to" to JsonPrimitive(phoneNumber)
            ))
            updateCallState(callId, CallState.DIALING)

            // Launch the system telephony intent to actually place the call
            launchCallIntent(phoneNumber, subscriptionId)

            Result.success(call.copy(state = CallState.DIALING))
        } catch (e: Exception) {
            updateCallState(callId, CallState.FAILED)
            eventEngine.emit(EventNames.CALL_FAILED, deviceId, mapOf(
                "call_id" to JsonPrimitive(callId),
                "error" to JsonPrimitive(e.message ?: "Unknown error")
            ))
            Result.failure(e)
        }
    }

    /**
     * Launch the appropriate telephony intent to place a call.
     *
     * Uses ACTION_CALL (places call directly) when we have CALL_PHONE permission,
     * otherwise falls back to ACTION_DIAL (opens dialer with number pre-filled).
     */
    private fun launchCallIntent(phoneNumber: String, subscriptionId: Int? = null) {
        val uri = if (subscriptionId != null && subscriptionId > 0) {
            Uri.parse("tel:$phoneNumber?subscription=$subscriptionId")
        } else {
            Uri.parse("tel:$phoneNumber")
        }

        val action = if (context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Intent.ACTION_CALL
        } else {
            Log.w(TAG, "CALL_PHONE permission not granted, falling back to ACTION_DIAL")
            Intent.ACTION_DIAL
        }

        val intent = Intent(action).apply {
            data = uri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    suspend fun onIncomingCall(
        callId: String,
        phoneNumber: String,
        displayName: String?,
        deviceId: String,
        subscriptionId: Int = 0
    ) {
        val call = io.opencell.core.model.Call(
            id = callId,
            deviceId = deviceId,
            subscriptionId = subscriptionId,
            direction = CallDirection.INBOUND,
            from = phoneNumber,
            to = "",
            state = CallState.RINGING,
            startedAt = System.currentTimeMillis(),
            displayName = displayName
        )
        callDao.upsertCall(call.toCallEntity())
        _currentCall.value = call
        _activeCalls.value = listOf(call)
        eventEngine.emit(EventNames.CALL_INCOMING, deviceId, mapOf(
            "call_id" to JsonPrimitive(callId),
            "from" to JsonPrimitive(phoneNumber)
        ))
    }

    /**
     * Transition a call to ACTIVE (answered).
     *
     * Accepts calls in RINGING, DIALING, or CREATED state.
     * DIALING → ACTIVE handles API-initiated outbound calls that connected.
     * CREATED → ACTIVE handles edge cases where the call record exists but
     * the Telecom framework has not yet transitioned through DIALING.
     */
    suspend fun answerCall(callId: String): Result<io.opencell.core.model.Call> {
        val entity = callDao.getCall(callId) ?: return Result.failure(IllegalStateException("Call not found"))
        val allowedStates = setOf(
            CallState.RINGING.name,
            CallState.DIALING.name,
            CallState.CREATED.name
        )
        if (entity.state !in allowedStates) {
            return Result.failure(IllegalStateException("Call cannot be answered from state: ${entity.state}"))
        }
        updateCallState(callId, CallState.ACTIVE, answeredAt = System.currentTimeMillis())
        val updated = callDao.getCall(callId) ?: return Result.failure(IllegalStateException("Call disappeared"))
        val call = updated.toCallDomain()
        _currentCall.value = call
        _activeCalls.value = listOf(call)
        return Result.success(call)
    }

    suspend fun rejectCall(callId: String): Result<Unit> {
        callDao.getCall(callId) ?: return Result.failure(IllegalStateException("Call not found"))
        updateCallState(callId, CallState.REJECTED, endedAt = System.currentTimeMillis())
        _currentCall.value = null
        _activeCalls.value = emptyList()
        auditLogDao.insertEntry(AuditLogEntity(
            id = CryptoUtils.generateId("audit"),
            action = "call.rejected", actorType = "user", actorId = "local",
            resourceType = "call", resourceId = callId
        ))
        return Result.success(Unit)
    }

    suspend fun hangupCall(callId: String): Result<Unit> {
        val entity = callDao.getCall(callId) ?: return Result.failure(IllegalStateException("Call not found"))
        if (entity.state in listOf(CallState.ENDED.name, CallState.FAILED.name)) {
            return Result.failure(IllegalStateException("Call already ended"))
        }
        val answered = entity.answeredAt
        val duration = if (answered != null) System.currentTimeMillis() - answered else 0L
        updateCallState(callId, CallState.ENDED, endedAt = System.currentTimeMillis(), durationMs = duration)
        _currentCall.value = null
        _activeCalls.value = emptyList()
        return Result.success(Unit)
    }

    suspend fun holdCall(callId: String): Result<io.opencell.core.model.Call> {
        val entity = callDao.getCall(callId) ?: return Result.failure(IllegalStateException("Call not found"))
        if (entity.state != CallState.ACTIVE.name) {
            return Result.failure(IllegalStateException("Only active calls can be held"))
        }
        updateCallState(callId, CallState.ON_HOLD)
        val updated = callDao.getCall(callId) ?: return Result.failure(IllegalStateException("Call disappeared"))
        return Result.success(updated.toCallDomain())
    }

    suspend fun resumeCall(callId: String): Result<io.opencell.core.model.Call> {
        val entity = callDao.getCall(callId) ?: return Result.failure(IllegalStateException("Call not found"))
        if (entity.state != CallState.ON_HOLD.name) {
            return Result.failure(IllegalStateException("Call is not on hold"))
        }
        updateCallState(callId, CallState.ACTIVE)
        val updated = callDao.getCall(callId) ?: return Result.failure(IllegalStateException("Call disappeared"))
        return Result.success(updated.toCallDomain())
    }

    fun getCallHistory(): Flow<List<CallEntity>> {
        return callDao.getAllCalls()
    }

    suspend fun getCallEntityById(callId: String): io.opencell.core.database.entity.CallEntity? {
        return callDao.getCall(callId)
    }

    /**
     * Find the most recent outbound call record for a given phone number.
     * Used by [InCallService] to match a Telecom framework call to an existing
     * [CallEngine] record that was created by [makeCall].
     */
    suspend fun findRecentOutboundCall(phoneNumber: String): CallEntity? {
        return callDao.getRecentOutboundCallByNumber(phoneNumber)
    }

    private suspend fun updateCallState(
        callId: String,
        state: CallState,
        answeredAt: Long? = null,
        endedAt: Long? = null,
        durationMs: Long = 0
    ) {
        callDao.updateCallState(callId, state.name, answeredAt = answeredAt, endedAt = endedAt, durationMs = durationMs)
        callDao.insertCallEvent(CallEventEntity(
            id = CryptoUtils.generateId("evt"),
            callId = callId,
            state = state.name,
            timestamp = System.currentTimeMillis()
        ))
        val entity = callDao.getCall(callId) ?: return
        val eventName = when (state) {
            CallState.CREATED -> EventNames.CALL_CREATED
            CallState.QUEUED -> EventNames.CALL_QUEUED
            CallState.DIALING -> EventNames.CALL_DIALING
            CallState.RINGING -> EventNames.CALL_RINGING
            CallState.ACTIVE -> EventNames.CALL_ACTIVE
            CallState.ON_HOLD -> EventNames.CALL_HELD
            CallState.ENDING -> EventNames.CALL_ENDING
            CallState.ENDED -> EventNames.CALL_ENDED
            CallState.FAILED -> EventNames.CALL_FAILED
            CallState.BUSY -> EventNames.CALL_FAILED
            CallState.MISSED -> EventNames.CALL_ENDED
            CallState.REJECTED -> EventNames.CALL_ENDED
        }
        eventEngine.emit(eventName, entity.deviceId, mapOf(
            "call_id" to JsonPrimitive(callId),
            "state" to JsonPrimitive(state.name)
        ))
    }
}

fun io.opencell.core.model.Call.toCallEntity() = CallEntity(
    id = id, deviceId = deviceId, subscriptionId = subscriptionId,
    direction = direction.name, fromNumber = from, toNumber = to,
    state = state.name, startedAt = startedAt, answeredAt = answeredAt,
    endedAt = endedAt, audioState = audioState.name,
    recordingState = recordingState.name, displayName = displayName,
    isEmergency = isEmergency, durationMs = durationMs
)

fun CallEntity.toCallDomain() = io.opencell.core.model.Call(
    id = id, deviceId = deviceId, subscriptionId = subscriptionId,
    direction = CallDirection.valueOf(direction), from = fromNumber, to = toNumber,
    state = CallState.valueOf(state), startedAt = startedAt, answeredAt = answeredAt,
    endedAt = endedAt, audioState = AudioState.valueOf(audioState),
    recordingState = RecordingState.valueOf(recordingState),
    displayName = displayName, isEmergency = isEmergency
)
