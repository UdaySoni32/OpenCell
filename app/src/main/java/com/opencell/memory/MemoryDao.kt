package com.opencell.memory

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Something the assistant learned. [contactKey] (last 10 digits) scopes it to one person; null = about the user. */
@Entity(tableName = "memory_facts")
data class MemoryFact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactKey: String?,
    val text: String,
    val source: String,
    val createdAtMillis: Long,
)

@Dao
interface MemoryDao {
    @Insert
    suspend fun insert(fact: MemoryFact): Long

    @Delete
    suspend fun delete(fact: MemoryFact)

    @Query("SELECT * FROM memory_facts ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<MemoryFact>>

    /** Facts about the user plus facts about this contact. */
    @Query("SELECT * FROM memory_facts WHERE contactKey IS NULL OR contactKey = :contactKey ORDER BY createdAtMillis DESC LIMIT :limit")
    suspend fun relevant(contactKey: String?, limit: Int = 40): List<MemoryFact>
}
