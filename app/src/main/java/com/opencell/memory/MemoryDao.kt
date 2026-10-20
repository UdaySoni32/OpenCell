package com.opencell.memory

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

enum class MemoryCategory {
    PREFERENCE,
    IDENTITY,
    RELATIONSHIP,
    SCHEDULE,
    GOAL,
    COMMITMENT,
    INSTRUCTION,
    GENERAL,
    SENSITIVE,
}

enum class MemorySourceType {
    USER_CONFIRMED,
    USER_MESSAGE,
    SMS,
    CALL,
    MANUAL,
    INFERRED,
}

/** Something the assistant learned. [contactKey] (last 10 digits) scopes it to one person; null = about the user. */
@Entity(tableName = "memory_facts")
data class MemoryFact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactKey: String?,
    val text: String,
    val source: String,
    val createdAtMillis: Long,
    @ColumnInfo(defaultValue = "'GENERAL'") val category: MemoryCategory = MemoryCategory.GENERAL,
    @ColumnInfo(defaultValue = "'MANUAL'") val sourceType: MemorySourceType = MemorySourceType.MANUAL,
    @ColumnInfo(defaultValue = "1.0") val confidence: Double = 1.0,
    val sourceReference: String? = null,
    @ColumnInfo(defaultValue = "0") val updatedAtMillis: Long = createdAtMillis,
    val expiresAtMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val needsConfirmation: Boolean = false,
    val lastUsedAtMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val retracted: Boolean = false,
)

@Dao
interface MemoryDao {
    @Insert
    suspend fun insert(fact: MemoryFact): Long

    @Delete
    suspend fun delete(fact: MemoryFact)

    @Query("SELECT * FROM memory_facts WHERE retracted = 0 ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<MemoryFact>>

    /** Facts about the user plus facts about this contact. */
    @Query(
        """SELECT * FROM memory_facts
           WHERE retracted = 0
             AND (contactKey IS NULL OR contactKey = :contactKey)
             AND (:includeUnconfirmed = 1 OR needsConfirmation = 0)
             AND (expiresAtMillis IS NULL OR expiresAtMillis > :now)
           ORDER BY createdAtMillis DESC LIMIT :limit""",
    )
    suspend fun relevant(
        contactKey: String?,
        limit: Int = 40,
        now: Long = System.currentTimeMillis(),
        includeUnconfirmed: Boolean = false,
    ): List<MemoryFact>

    @Query("UPDATE memory_facts SET needsConfirmation = 0, sourceType = 'USER_CONFIRMED', confidence = 1.0, updatedAtMillis = :now WHERE id = :id")
    suspend fun confirm(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE memory_facts SET text = :text, updatedAtMillis = :now WHERE id = :id")
    suspend fun updateText(id: Long, text: String, now: Long = System.currentTimeMillis())
}
