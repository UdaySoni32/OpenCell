package com.example.opencell.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    FAILED,
    RECEIVED
}

@Serializable
data class MessageRecord(
    val id: Long = 0,
    val address: String,
    val contactName: String? = null,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isIncoming: Boolean = true,
    val isRead: Boolean = true,
    val status: MessageStatus = if (isIncoming) MessageStatus.RECEIVED else MessageStatus.SENT,
    val threadId: Long = 0L
)
