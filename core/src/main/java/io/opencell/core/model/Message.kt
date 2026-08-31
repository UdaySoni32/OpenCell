package io.opencell.core.model

import kotlinx.serialization.Serializable

enum class MessageType {
    SMS,
    MMS
}

enum class MessageDirection {
    INBOUND,
    OUTBOUND
}

enum class MessageState {
    CREATED,
    QUEUED,
    SENDING,
    SENT,
    DELIVERED,
    RECEIVED,
    FAILED,
    PENDING,
    NOT_APPLICABLE
}

@Serializable
data class Message(
    val id: String,
    val deviceId: String,
    val subscriptionId: Int = 0,
    val type: MessageType = MessageType.SMS,
    val direction: MessageDirection,
    val sender: String,
    val recipient: String,
    val body: String,
    val media: List<MediaAttachment> = emptyList(),
    val state: MessageState = MessageState.CREATED,
    val createdAt: Long = System.currentTimeMillis(),
    val sentAt: Long? = null,
    val deliveredAt: Long? = null,
    val readAt: Long? = null,
    val errorCode: Int? = null,
    val errorMessage: String? = null,
    val threadId: String? = null,
    val seen: Boolean = false
)

@Serializable
data class MediaAttachment(
    val id: String,
    val type: String,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val size: Long = 0
)

@Serializable
data class MessageEvent(
    val id: String,
    val messageId: String,
    val state: MessageState,
    val timestamp: Long = System.currentTimeMillis(),
    val details: String? = null
)

@Serializable
data class Conversation(
    val threadId: String,
    val contactAddress: String,
    val contactName: String? = null,
    val lastMessage: String = "",
    val lastMessageAt: Long = 0,
    val unreadCount: Int = 0,
    val isGroup: Boolean = false
)
