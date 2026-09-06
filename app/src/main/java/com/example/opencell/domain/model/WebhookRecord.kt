package com.example.opencell.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class WebhookRecord(
    val id: String,
    val url: String,
    val events: List<String>,
    val secret: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val EVENT_CALL_CREATED = "call.created"
        const val EVENT_CALL_STATE_CHANGED = "call.state_changed"
        const val EVENT_MESSAGE_CREATED = "message.created"
        const val EVENT_MESSAGE_STATE_CHANGED = "message.state_changed"

        val ALL_EVENTS = listOf(
            EVENT_CALL_CREATED,
            EVENT_CALL_STATE_CHANGED,
            EVENT_MESSAGE_CREATED,
            EVENT_MESSAGE_STATE_CHANGED
        )
    }
}
