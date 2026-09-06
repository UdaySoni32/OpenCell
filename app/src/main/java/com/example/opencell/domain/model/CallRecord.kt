package com.example.opencell.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CallType {
    INCOMING, OUTGOING, MISSED
}

@Serializable
data class CallRecord(
    val id: Long = 0,
    val phoneNumber: String,
    val contactName: String? = null,
    val callType: CallType,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0
)
