package io.opencell.core.database.dao

import androidx.room.*
import io.opencell.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiKeyDao {
    // API Keys
    @Query("SELECT * FROM api_keys WHERE projectId = :projectId AND isActive = 1")
    fun getActiveKeysForProject(projectId: String): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys WHERE keyHash = :keyHash AND isActive = 1")
    suspend fun getApiKeyByHash(keyHash: String): ApiKeyEntity?

    @Query("SELECT * FROM api_keys WHERE id = :id")
    suspend fun getApiKey(id: String): ApiKeyEntity?

    @Query("SELECT * FROM api_keys WHERE id = :id")
    fun observeApiKey(id: String): Flow<ApiKeyEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApiKey(key: ApiKeyEntity)

    @Update
    suspend fun updateApiKey(key: ApiKeyEntity)

    @Query("UPDATE api_keys SET isActive = 0, revokedAt = :revokedAt WHERE id = :id")
    suspend fun revokeApiKey(id: String, revokedAt: Long = System.currentTimeMillis())

    @Query("UPDATE api_keys SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: String, timestamp: Long = System.currentTimeMillis())

    // Projects
    @Query("SELECT * FROM projects")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProject(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)
}

@Dao
interface WebhookDao {
    @Query("SELECT * FROM webhooks WHERE projectId = :projectId AND isActive = 1")
    fun getActiveWebhooksForProject(projectId: String): Flow<List<WebhookEntity>>

    @Query("SELECT * FROM webhooks WHERE id = :id")
    suspend fun getWebhook(id: String): WebhookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWebhook(webhook: WebhookEntity)

    @Update
    suspend fun updateWebhook(webhook: WebhookEntity)

    @Query("DELETE FROM webhooks WHERE id = :id")
    suspend fun deleteWebhook(id: String)

    // Webhook deliveries
    @Insert
    suspend fun insertDelivery(delivery: WebhookDeliveryEntity)

    @Query("SELECT * FROM webhook_deliveries WHERE webhookId = :webhookId ORDER BY deliveredAt DESC LIMIT :limit")
    fun getDeliveries(webhookId: String, limit: Int = 50): Flow<List<WebhookDeliveryEntity>>
}

@Dao
interface EventDao {
    @Insert
    suspend fun insertEvent(event: EventEntity)

    @Query("SELECT * FROM events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEvents(limit: Int = 100): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE deviceId = :deviceId ORDER BY timestamp DESC LIMIT :limit")
    fun getEventsForDevice(deviceId: String, limit: Int = 100): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE name = :eventName ORDER BY timestamp DESC LIMIT :limit")
    fun getEventsByName(eventName: String, limit: Int = 50): Flow<List<EventEntity>>

    @Query("SELECT MAX(sequenceNumber) FROM events WHERE deviceId = :deviceId")
    suspend fun getMaxSequenceNumber(deviceId: String): Long?

    @Query("SELECT * FROM events WHERE sequenceNumber > :sinceSeq AND deviceId = :deviceId ORDER BY sequenceNumber ASC")
    fun getEventsSince(deviceId: String, sinceSeq: Long): Flow<List<EventEntity>>
}

@Dao
interface AuditLogDao {
    @Insert
    suspend fun insertEntry(entry: AuditLogEntity)

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEntries(limit: Int = 200): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE action = :action ORDER BY timestamp DESC LIMIT :limit")
    fun getEntriesByAction(action: String, limit: Int = 50): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE actorType = :actorType AND actorId = :actorId ORDER BY timestamp DESC LIMIT :limit")
    fun getEntriesByActor(actorType: String, actorId: String, limit: Int = 50): Flow<List<AuditLogEntity>>
}

@Dao
interface ConfigurationDao {
    @Query("SELECT * FROM configuration")
    fun getAllConfig(): Flow<List<ConfigurationEntity>>

    @Query("SELECT value FROM configuration WHERE `key` = :key")
    suspend fun getConfigValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setConfigValue(config: ConfigurationEntity)

    @Query("DELETE FROM configuration WHERE `key` = :key")
    suspend fun deleteConfigValue(key: String)
}

@Dao
interface CommandDao {
    @Query("SELECT * FROM commands WHERE deviceId = :deviceId AND status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingCommands(deviceId: String): List<CommandEntity>

    @Query("SELECT * FROM commands WHERE id = :id")
    suspend fun getCommand(id: String): CommandEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCommand(command: CommandEntity)

    @Query("UPDATE commands SET status = :status, executedAt = :executedAt WHERE id = :id")
    suspend fun updateCommandStatus(id: String, status: String, executedAt: Long? = System.currentTimeMillis())

    @Query("UPDATE commands SET status = :status, completedAt = :completedAt, result = :result WHERE id = :id")
    suspend fun completeCommand(id: String, status: String, result: String?, completedAt: Long = System.currentTimeMillis())

    @Query("UPDATE commands SET status = 'FAILED', error = :error, completedAt = :completedAt WHERE id = :id")
    suspend fun failCommand(id: String, error: String, completedAt: Long = System.currentTimeMillis())
}
