package com.opencell.assistant

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.opencell.ai.Role
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "assistant_threads")
data class AssistantThread(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "assistant_messages",
    foreignKeys = [
        ForeignKey(
            entity = AssistantThread::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["threadId", "createdAtMillis"])],
)
data class AssistantMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    val role: Role,
    val content: String,
    val createdAtMillis: Long,
)

@Dao
interface AssistantDao {
    @Insert
    suspend fun insertThread(thread: AssistantThread): Long

    @Query("SELECT * FROM assistant_threads ORDER BY updatedAtMillis DESC, id DESC")
    fun observeThreads(): Flow<List<AssistantThread>>

    @Query("SELECT * FROM assistant_threads ORDER BY updatedAtMillis DESC, id DESC LIMIT 1")
    suspend fun latestThread(): AssistantThread?

    @Insert
    suspend fun insertMessage(message: AssistantMessage): Long

    @Query("SELECT * FROM assistant_messages WHERE threadId = :threadId ORDER BY createdAtMillis ASC, id ASC")
    fun observeMessages(threadId: Long): Flow<List<AssistantMessage>>

    @Query("SELECT * FROM assistant_messages WHERE threadId = :threadId ORDER BY createdAtMillis ASC, id ASC")
    suspend fun messages(threadId: Long): List<AssistantMessage>

    @Query("UPDATE assistant_threads SET updatedAtMillis = :updatedAtMillis WHERE id = :threadId")
    suspend fun touchThread(threadId: Long, updatedAtMillis: Long)
}
