package com.example.opencell.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TelephonyEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val eventType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val payload: Map<String, String> = emptyMap()
)
