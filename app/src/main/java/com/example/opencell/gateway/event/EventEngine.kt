package com.example.opencell.gateway.event

import com.example.opencell.domain.model.ServerLogType
import com.example.opencell.domain.model.TelephonyEvent
import com.example.opencell.domain.model.WebhookRecord
import com.example.opencell.gateway.logging.ServerLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class EventEngine(
    private val serverLogRepository: ServerLogRepository = ServerLogRepository.instance,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val _events = MutableSharedFlow<TelephonyEvent>(
        replay = 10,
        extraBufferCapacity = 100
    )
    val events: SharedFlow<TelephonyEvent> = _events.asSharedFlow()

    fun emitEvent(event: TelephonyEvent) {
        externalScope.launch {
            _events.emit(event)
            serverLogRepository.log(
                type = ServerLogType.SYSTEM,
                message = "Event Emitted: ${event.eventType}",
                details = "EventId: ${event.eventId}, Payload: ${event.payload}"
            )
        }
    }

    fun emitCallCreated(callId: String, phoneNumber: String, type: String) {
        emitEvent(
            TelephonyEvent(
                eventType = WebhookRecord.EVENT_CALL_CREATED,
                payload = mapOf(
                    "callId" to callId,
                    "phoneNumber" to phoneNumber,
                    "callType" to type
                )
            )
        )
    }

    fun emitCallStateChanged(callId: String, phoneNumber: String, state: String) {
        emitEvent(
            TelephonyEvent(
                eventType = WebhookRecord.EVENT_CALL_STATE_CHANGED,
                payload = mapOf(
                    "callId" to callId,
                    "phoneNumber" to phoneNumber,
                    "state" to state
                )
            )
        )
    }

    fun emitMessageCreated(messageId: String, address: String, body: String, isIncoming: Boolean) {
        emitEvent(
            TelephonyEvent(
                eventType = WebhookRecord.EVENT_MESSAGE_CREATED,
                payload = mapOf(
                    "messageId" to messageId,
                    "address" to address,
                    "body" to body,
                    "isIncoming" to isIncoming.toString()
                )
            )
        )
    }

    fun emitMessageStateChanged(messageId: String, status: String) {
        emitEvent(
            TelephonyEvent(
                eventType = WebhookRecord.EVENT_MESSAGE_STATE_CHANGED,
                payload = mapOf(
                    "messageId" to messageId,
                    "status" to status
                )
            )
        )
    }

    companion object {
        val instance by lazy { EventEngine() }
    }
}
