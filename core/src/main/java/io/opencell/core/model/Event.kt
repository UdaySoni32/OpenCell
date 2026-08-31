package io.opencell.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Normalized event names for the unified event engine.
 * The same internal event engine powers: UI, local API, WebSocket, webhooks, and audit logs.
 */
object EventNames {
    // Device events
    const val DEVICE_CONNECTED = "device.connected"
    const val DEVICE_OFFLINE = "device.offline"
    const val DEVICE_UPDATED = "device.updated"

    // Message events
    const val MESSAGE_CREATED = "message.created"
    const val MESSAGE_QUEUED = "message.queued"
    const val MESSAGE_SENDING = "message.sending"
    const val MESSAGE_SENT = "message.sent"
    const val MESSAGE_DELIVERED = "message.delivered"
    const val MESSAGE_RECEIVED = "message.received"
    const val MESSAGE_FAILED = "message.failed"

    // Call events
    const val CALL_CREATED = "call.created"
    const val CALL_INCOMING = "call.incoming"
    const val CALL_QUEUED = "call.queued"
    const val CALL_DIALING = "call.dialing"
    const val CALL_RINGING = "call.ringing"
    const val CALL_ANSWERED = "call.answered"
    const val CALL_ACTIVE = "call.active"
    const val CALL_HELD = "call.held"
    const val CALL_RESUMED = "call.resumed"
    const val CALL_ENDING = "call.ending"
    const val CALL_ENDED = "call.ended"
    const val CALL_FAILED = "call.failed"

    // USSD events
    const val USSD_COMPLETED = "ussd.completed"
    const val USSD_FAILED = "ussd.failed"

    // SIM/Network events
    const val SIM_CHANGED = "sim.changed"
    const val NETWORK_CHANGED = "network.changed"

    // Audio events
    const val AUDIO_STARTED = "audio.started"
    const val AUDIO_STOPPED = "audio.stopped"

    // API events
    const val API_KEY_CREATED = "api_key.created"
    const val API_KEY_REVOKED = "api_key.revoked"
    const val WEBHOOK_TRIGGERED = "webhook.triggered"
}

@Serializable
data class Event(
    val id: String,
    val name: String,
    val deviceId: String,
    val data: Map<String, kotlinx.serialization.json.JsonElement?> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val sequenceNumber: Long = 0,
    val requestId: String? = null
)
