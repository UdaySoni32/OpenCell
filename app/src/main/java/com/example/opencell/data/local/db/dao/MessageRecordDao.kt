package com.example.opencell.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.opencell.data.local.db.entity.MessageRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageRecordDao {
    @Query("SELECT * FROM message_records ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MessageRecordEntity>>

    @Query("SELECT * FROM message_records WHERE id IN (SELECT MAX(id) FROM message_records GROUP BY address) ORDER BY timestamp DESC")
    fun getConversationSummaries(): Flow<List<MessageRecordEntity>>

    @Query("SELECT * FROM message_records WHERE address = :address ORDER BY timestamp ASC")
    fun getMessagesForAddress(address: String): Flow<List<MessageRecordEntity>>

    @Query("SELECT * FROM message_records WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: Long): MessageRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageRecordEntity): Long

    @Query("UPDATE message_records SET status = :status WHERE id = :id")
    suspend fun updateMessageStatus(id: Long, status: String)

    @Delete
    suspend fun deleteMessage(message: MessageRecordEntity)

    @Query("DELETE FROM message_records")
    suspend fun clearAllMessages()
}
