package com.example.opencell.domain.model

enum class CallState {
    NEW,
    DIALING,
    RINGING,
    ACTIVE,
    HELD,
    ENDING,
    ENDED,
    MISSED,
    REJECTED,
    FAILED
}

data class CallSession(
    val callId: String = System.currentTimeMillis().toString(),
    val phoneNumber: String,
    val contactName: String? = null,
    val state: CallState = CallState.NEW,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isOnHold: Boolean = false,
    val startTimeMillis: Long = System.currentTimeMillis(),
    val connectTimeMillis: Long? = null,
    val durationSeconds: Int = 0,
    val isIncoming: Boolean = false,
    val isSimulated: Boolean = false,
    val disconnectReason: String? = null
)
