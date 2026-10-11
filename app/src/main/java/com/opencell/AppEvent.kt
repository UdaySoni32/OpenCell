package com.opencell

/** App-wide events; the gateway streams these to paired computers and the AI agent reacts to them. */
sealed interface AppEvent {
    val timestampMillis: Long

    data class SmsReceived(val messageId: Long, val from: String, val body: String, override val timestampMillis: Long) : AppEvent
}
