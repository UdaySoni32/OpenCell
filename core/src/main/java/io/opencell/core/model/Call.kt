package io.opencell.core.model

import kotlinx.serialization.Serializable

/**
 * Normalized call states mapping to Android Telecom call states.
 */
enum class CallState {
    CREATED,
    QUEUED,
    DIALING,
    RINGING,
    ACTIVE,
    ON_HOLD,
    ENDING,
    ENDED,
    FAILED,
    BUSY,
    MISSED,
    REJECTED
}

enum class CallDirection {
    INBOUND,
    OUTBOUND
}

enum class AudioState {
    NONE,
    SPEAKER,
    EARPIECE,
    BLUETOOTH,
    WIRED_HEADSET,
    STREAMING
}

enum class RecordingState {
    NOT_SUPPORTED,
    NOT_RECORDING,
    RECORDING,
    PAUSED,
    FAILED
}

@Serializable
data class Call(
    val id: String,
    val deviceId: String,
    val subscriptionId: Int = 0,
    val direction: CallDirection,
    val from: String,
    val to: String,
    val state: CallState = CallState.CREATED,
    val startedAt: Long = System.currentTimeMillis(),
    val answeredAt: Long? = null,
    val endedAt: Long? = null,
    val audioState: AudioState = AudioState.EARPIECE,
    val recordingState: RecordingState = RecordingState.NOT_SUPPORTED,
    val displayName: String? = null,
    val isEmergency: Boolean = false
) {
    val durationMs: Long
        get() {
            val end = endedAt ?: System.currentTimeMillis()
            val start = answeredAt ?: startedAt
            return if (state == CallState.ACTIVE || state == CallState.ON_HOLD || state == CallState.ENDING || state == CallState.ENDED) {
                maxOf(0L, end - start)
            } else 0L
        }

    val durationSeconds: Long get() = durationMs / 1000
}

@Serializable
data class CallEvent(
    val id: String,
    val callId: String,
    val state: CallState,
    val timestamp: Long = System.currentTimeMillis(),
    val details: String? = null
)
