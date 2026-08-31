package io.opencell.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val id: String,
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

@Serializable
data class SimInfo(
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

@Serializable
data class NetworkInfo(
    val type: String,
    val name: String?,
    val isAvailable: Boolean,
    val isConnected: Boolean,
    val isRoaming: Boolean,
    val signalStrength: Int? = null,
    val signalLevel: Int? = null,
    val asuLevel: Int? = null,
    val dbmLevel: Int? = null
)

@Serializable
data class ApiKey(
    val id: String,
    val projectId: String,
    val name: String,
    val keyPrefix: String,
    val keyHash: String,
    val scopes: List<String>,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val lastUsedAt: Long? = null,
    val revokedAt: Long? = null
)

@Serializable
data class Project(
    val id: String,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class Webhook(
    val id: String,
    val projectId: String,
    val url: String,
    val secret: String,
    val events: List<String>,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggeredAt: Long? = null,
    val failureCount: Int = 0
)

@Serializable
data class WebhookDelivery(
    val id: String,
    val webhookId: String,
    val event: String,
    val payload: String,
    val statusCode: Int? = null,
    val response: String? = null,
    val deliveredAt: Long = System.currentTimeMillis(),
    val success: Boolean = false
)

@Serializable
data class UssdResponse(
    val requestId: String,
    val response: String,
    val isComplete: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class AuditLogEntry(
    val id: String,
    val action: String,
    val actorType: String,
    val actorId: String,
    val resourceType: String,
    val resourceId: String,
    val details: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class DeviceConfig(
    val apiPort: Int = 8900,
    val apiHost: String = "127.0.0.1",
    val apiEnabled: Boolean = true,
    val remoteAccessEnabled: Boolean = false,
    val webhooksEnabled: Boolean = true,
    val maxRateLimitPerMinute: Int = 60,
    val callRecordingEnabled: Boolean = false
)
