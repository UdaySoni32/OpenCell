package com.opencell.assistant

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class TaskStatus { OPEN, DONE, CANCELLED }
enum class ReminderStatus { PENDING, FIRED, CANCELLED }

@Entity(tableName = "assistant_tasks")
data class AssistantTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val details: String? = null,
    val dueAtMillis: Long? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    val source: String = "Assistant",
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(tableName = "assistant_reminders")
data class AssistantReminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerAtMillis: Long,
    val status: ReminderStatus = ReminderStatus.PENDING,
    val taskId: Long? = null,
    val createdAtMillis: Long,
)

@Dao
interface AssistantTaskDao {
    @Insert
    suspend fun insertTask(task: AssistantTask): Long

    @Update
    suspend fun updateTask(task: AssistantTask)

    @Query("SELECT * FROM assistant_tasks WHERE status = 'OPEN' ORDER BY CASE WHEN dueAtMillis IS NULL THEN 1 ELSE 0 END, dueAtMillis ASC, createdAtMillis DESC")
    fun observeOpenTasks(): Flow<List<AssistantTask>>

    @Query("SELECT * FROM assistant_tasks WHERE status = 'OPEN' ORDER BY createdAtMillis DESC")
    suspend fun openTasks(): List<AssistantTask>

    @Query("SELECT * FROM assistant_tasks WHERE id = :id")
    suspend fun task(id: Long): AssistantTask?

    @Query("UPDATE assistant_tasks SET status = 'DONE', updatedAtMillis = :at WHERE id = :id")
    suspend fun completeTask(id: Long, at: Long = System.currentTimeMillis())

    @Query("SELECT * FROM assistant_reminders WHERE status = 'PENDING' ORDER BY triggerAtMillis ASC")
    suspend fun pendingReminders(): List<AssistantReminder>

    @Insert
    suspend fun insertReminder(reminder: AssistantReminder): Long

    @Query("UPDATE assistant_reminders SET status = 'FIRED' WHERE id = :id")
    suspend fun markReminderFired(id: Long)
}

data class ReminderDraft(val title: String, val triggerAtMillis: Long)
data class TaskDraft(val title: String, val dueAtMillis: Long? = null)

/** Small, deterministic command parser for useful reminders without requiring a provider round trip. */
object AssistantTaskCommandParser {
    fun reminder(input: String, now: Long = System.currentTimeMillis()): ReminderDraft? {
        val text = input.trim().replace(Regex("\\s+"), " ")
        val relative = Regex("^remind me (?:to )?(.+?) in (\\d+) minutes?$", RegexOption.IGNORE_CASE).find(text)
        if (relative != null) {
            val minutes = relative.groupValues[2].toLongOrNull()?.coerceIn(1, 60 * 24 * 365) ?: return null
            return ReminderDraft(relative.groupValues[1].trim().take(300), now + minutes * 60_000L)
        }
        val tomorrow = Regex(
            "^remind me (?:to )?(.+?) tomorrow at (\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$",
            RegexOption.IGNORE_CASE,
        ).find(text) ?: return null
        val hour = tomorrow.groupValues[2].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = tomorrow.groupValues[3].ifBlank { "0" }.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        val amPm = tomorrow.groupValues[4].lowercase()
        val normalizedHour = when {
            amPm == "am" && hour == 12 -> 0
            amPm == "pm" && hour < 12 -> hour + 12
            else -> hour
        }
        val local = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().plusDays(1)
            .atTime(normalizedHour, minute)
        return ReminderDraft(tomorrow.groupValues[1].trim().take(300), local.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
    }

    fun task(input: String): TaskDraft? {
        val text = input.trim().replace(Regex("\\s+"), " ")
        val title = Regex("^(?:create )?(?:a )?(?:task|todo)[: ]+(.+)$", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.length in 3..300 } ?: return null
        return TaskDraft(title)
    }

    fun completeTask(input: String): String? = Regex("^(?:complete|finish|mark done) (?:the )?(?:task|todo)[: ]+(.+)$", RegexOption.IGNORE_CASE)
        .find(input.trim())?.groupValues?.get(1)?.trim()?.takeIf { it.length in 3..300 }
}
