package com.example.opencell.domain.model

import java.util.UUID

enum class ServerLogType {
    REQUEST,
    WEBSOCKET,
    WEBHOOK,
    SYSTEM
}

data class ServerLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: ServerLogType,
    val message: String,
    val details: String? = null
)
