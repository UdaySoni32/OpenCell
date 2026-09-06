package com.example.opencell.data.repository

import com.example.opencell.data.local.db.dao.MessageRecordDao
import com.example.opencell.data.local.db.entity.MessageRecordEntity
import com.example.opencell.domain.model.MessageRecord
import com.example.opencell.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface MessageRepository {
    fun getAllMessages(): Flow<List<MessageRecord>>
    fun getConversations(): Flow<List<MessageRecord>>
    fun getMessagesForAddress(address: String): Flow<List<MessageRecord>>
    suspend fun getMessageById(id: Long): MessageRecord?
    suspend fun insertMessage(message: MessageRecord): Long
    suspend fun updateMessageStatus(id: Long, status: MessageStatus)
    suspend fun deleteMessage(message: MessageRecord)
    suspend fun clearAllMessages()
}

class MessageRepositoryImpl(
    private val dao: MessageRecordDao
) : MessageRepository {

    override fun getAllMessages(): Flow<List<MessageRecord>> {
        return dao.getAllMessages().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getConversations(): Flow<List<MessageRecord>> {
        return dao.getConversationSummaries().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getMessagesForAddress(address: String): Flow<List<MessageRecord>> {
        return dao.getMessagesForAddress(address).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getMessageById(id: Long): MessageRecord? {
        return dao.getMessageById(id)?.toDomain()
    }

    override suspend fun insertMessage(message: MessageRecord): Long {
        return dao.insertMessage(message.toEntity())
    }

    override suspend fun updateMessageStatus(id: Long, status: MessageStatus) {
        dao.updateMessageStatus(id, status.name)
    }

    override suspend fun deleteMessage(message: MessageRecord) {
        dao.deleteMessage(message.toEntity())
    }

    override suspend fun clearAllMessages() {
        dao.clearAllMessages()
    }

    private fun MessageRecordEntity.toDomain(): MessageRecord {
        val messageStatus = try {
            MessageStatus.valueOf(status)
        } catch (_: Exception) {
            if (isIncoming) MessageStatus.RECEIVED else MessageStatus.SENT
        }
        return MessageRecord(
            id = id,
            address = address,
            contactName = contactName,
            body = body,
            timestamp = timestamp,
            isIncoming = isIncoming,
            isRead = isRead,
            status = messageStatus,
            threadId = threadId
        )
    }

    private fun MessageRecord.toEntity(): MessageRecordEntity {
        return MessageRecordEntity(
            id = id,
            address = address,
            contactName = contactName,
            body = body,
            timestamp = timestamp,
            isIncoming = isIncoming,
            isRead = isRead,
            status = status.name,
            threadId = threadId
        )
    }
}
