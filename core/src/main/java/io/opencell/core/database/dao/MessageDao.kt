package io.opencell.core.database.dao

import androidx.room.*
import io.opencell.core.database.entity.MessageEntity
import io.opencell.core.database.entity.MessageEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY createdAt DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE deviceId = :deviceId ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentMessages(deviceId: String, limit: Int = 200): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessage(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE id = :id")
    fun observeMessage(id: String): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY createdAt ASC")
    fun getMessagesByThread(threadId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sender = :address OR recipient = :address ORDER BY createdAt DESC")
    fun getMessagesByAddress(address: String): Flow<List<MessageEntity>>

    @Query("""
        SELECT DISTINCT threadId, 
            CASE WHEN direction = 'INBOUND' THEN sender ELSE recipient END as contactAddress,
            body as lastMessage,
            createdAt as lastMessageAt,
            0 as unreadCount,
            0 as isGroup
        FROM messages 
        WHERE threadId IS NOT NULL
        GROUP BY threadId 
        ORDER BY MAX(createdAt) DESC
    """)
    fun getConversations(): Flow<List<ConversationTuple>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET state = :state, sentAt = :sentAt WHERE id = :id")
    suspend fun updateMessageState(id: String, state: String, sentAt: Long? = null)

    @Query("UPDATE messages SET deliveredAt = :deliveredAt WHERE id = :id")
    suspend fun markDelivered(id: String, deliveredAt: Long = System.currentTimeMillis())

    @Query("UPDATE messages SET seen = 1, readAt = :readAt WHERE id = :id")
    suspend fun markSeen(id: String, readAt: Long = System.currentTimeMillis())

    @Query("UPDATE messages SET seen = 1 WHERE threadId = :threadId")
    suspend fun markThreadSeen(threadId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE threadId = :threadId AND seen = 0 AND direction = 'INBOUND'")
    suspend fun getUnreadCount(threadId: String): Int

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)

    // Message events
    @Insert
    suspend fun insertMessageEvent(event: MessageEventEntity)

    @Query("SELECT * FROM message_events WHERE messageId = :messageId ORDER BY timestamp ASC")
    fun getMessageEvents(messageId: String): Flow<List<MessageEventEntity>>

    @Query("SELECT * FROM message_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessageEvents(limit: Int = 100): Flow<List<MessageEventEntity>>
}

data class ConversationTuple(
    val threadId: String?,
    val contactAddress: String,
    val lastMessage: String,
    val lastMessageAt: Long,
    val unreadCount: Int,
    val isGroup: Int
)
