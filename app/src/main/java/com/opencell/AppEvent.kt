package com.opencell

/** App-wide events; the gateway streams these to paired computers and the AI agent reacts to them. */
sealed interface AppEvent {
    val timestampMillis: Long

    data class SmsReceived(val messageId: Long, val from: String, val body: String, override val timestampMillis: Long) : AppEvent

    data class CallStateChanged(
        val callId: String,
        val number: String?,
        val state: String,
        val isIncoming: Boolean,
        override val timestampMillis: Long,
    ) : AppEvent

    data class ApprovalChanged(val approvalId: Long, val status: String, val action: String, val target: String, override val timestampMillis: Long) : AppEvent
}
