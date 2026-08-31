package io.opencell.core.database.entity

import androidx.room.*

// ==================== Device ====================
@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val isActive: Boolean = true,
    val isOnline: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis()
)

// ==================== SIM ====================
@Entity(
    tableName = "sim_info",
    foreignKeys = [ForeignKey(
        entity = DeviceEntity::class,
        parentColumns = ["id"],
        childColumns = ["deviceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("deviceId")]
)
data class SimInfoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val subscriptionId: Int,
    val slotIndex: Int,
    val carrierName: String,
    val displayName: String,
    val number: String?,
    val mcc: String?,
    val mnc: String?,
    val countryCode: String?,
    val isActive: Boolean,
    val isEmbedded: Boolean = false
)

// ==================== Calls ====================
@Entity(
    tableName = "calls",
    foreignKeys = [ForeignKey(
        entity = DeviceEntity::class,
        parentColumns = ["id"],
        childColumns = ["deviceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("deviceId"), Index("fromNumber"), Index("toNumber"), Index("state"), Index("startedAt")]
)
data class CallEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val subscriptionId: Int = 0,
    val direction: String, // INBOUND, OUTBOUND
    val fromNumber: String,
    val toNumber: String,
    val state: String,
    val startedAt: Long,
    val answeredAt: Long? = null,
    val endedAt: Long? = null,
    val audioState: String = "EARPIECE",
    val recordingState: String = "NOT_SUPPORTED",
    val displayName: String? = null,
    val isEmergency: Boolean = false,
    val durationMs: Long = 0
)

@Entity(
    tableName = "call_events",
    foreignKeys = [ForeignKey(
        entity = CallEntity::class,
        parentColumns = ["id"],
        childColumns = ["callId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("callId"), Index("timestamp")]
)
data class CallEventEntity(
    @PrimaryKey val id: String,
    val callId: String,
    val state: String,
    val timestamp: Long,
    val details: String? = null
)

// ==================== Messages ====================
@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = DeviceEntity::class,
        parentColumns = ["id"],
        childColumns = ["deviceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("deviceId"), Index("sender"), Index("recipient"), Index("state"), Index("createdAt"), Index("threadId")]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val subscriptionId: Int = 0,
    val type: String, // SMS, MMS
    val direction: String, // INBOUND, OUTBOUND
    val sender: String,
    val recipient: String,
    val body: String,
    val state: String,
    val createdAt: Long,
    val sentAt: Long? = null,
    val deliveredAt: Long? = null,
    val readAt: Long? = null,
    val errorCode: Int? = null,
    val errorMessage: String? = null,
    val threadId: String? = null,
    val seen: Boolean = false
)

@Entity(
    tableName = "message_events",
    foreignKeys = [ForeignKey(
        entity = MessageEntity::class,
        parentColumns = ["id"],
        childColumns = ["messageId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("messageId"), Index("timestamp")]
)
data class MessageEventEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val state: String,
    val timestamp: Long,
    val details: String? = null
)

// ==================== API Keys ====================
@Entity(
    tableName = "api_keys",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId"), Index("keyHash")]
)
data class ApiKeyEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val keyPrefix: String,
    val keyHash: String,
    val scopes: String, // JSON array stored as string
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val lastUsedAt: Long? = null,
    val revokedAt: Long? = null
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// ==================== Webhooks ====================
@Entity(
    tableName = "webhooks",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class WebhookEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val url: String,
    val secret: String,
    val events: String, // JSON array stored as string
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggeredAt: Long? = null,
    val failureCount: Int = 0
)

@Entity(
    tableName = "webhook_deliveries",
    foreignKeys = [ForeignKey(
        entity = WebhookEntity::class,
        parentColumns = ["id"],
        childColumns = ["webhookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("webhookId"), Index("deliveredAt")]
)
data class WebhookDeliveryEntity(
    @PrimaryKey val id: String,
    val webhookId: String,
    val event: String,
    val payload: String,
    val statusCode: Int? = null,
    val response: String? = null,
    val deliveredAt: Long = System.currentTimeMillis(),
    val success: Boolean = false
)

// ==================== Events ====================
@Entity(
    tableName = "events",
    indices = [Index("deviceId"), Index("name"), Index("timestamp"), Index("sequenceNumber")]
)
data class EventEntity(
    @PrimaryKey val id: String,
    val name: String,
    val deviceId: String,
    val data: String, // JSON string
    val timestamp: Long,
    val sequenceNumber: Long,
    val requestId: String? = null
)

// ==================== Audit Log ====================
@Entity(
    tableName = "audit_logs",
    indices = [Index("timestamp"), Index("action"), Index("actorType")]
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val action: String,
    val actorType: String, // api_key, user, system
    val actorId: String,
    val resourceType: String,
    val resourceId: String,
    val details: String = "{}", // JSON string
    val timestamp: Long = System.currentTimeMillis()
)

// ==================== Configuration ====================
@Entity(tableName = "configuration")
data class ConfigurationEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

// ==================== Network Snapshots ====================
@Entity(
    tableName = "network_snapshots",
    indices = [Index("deviceId"), Index("timestamp")]
)
data class NetworkSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val type: String,
    val name: String?,
    val isAvailable: Boolean,
    val isConnected: Boolean,
    val isRoaming: Boolean,
    val signalStrength: Int? = null,
    val signalLevel: Int? = null,
    val asuLevel: Int? = null,
    val dbmLevel: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// ==================== Commands ====================
@Entity(
    tableName = "commands",
    indices = [Index("deviceId"), Index("status"), Index("createdAt")]
)
data class CommandEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val type: String,
    val payload: String,
    val status: String, // PENDING, EXECUTING, COMPLETED, FAILED, TIMEOUT
    val createdAt: Long = System.currentTimeMillis(),
    val executedAt: Long? = null,
    val completedAt: Long? = null,
    val result: String? = null,
    val error: String? = null,
    val requestId: String? = null
)
