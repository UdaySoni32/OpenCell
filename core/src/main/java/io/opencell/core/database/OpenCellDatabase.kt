package io.opencell.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import io.opencell.core.database.dao.*
import io.opencell.core.database.entity.*

@Database(
    entities = [
        DeviceEntity::class,
        SimInfoEntity::class,
        CallEntity::class,
        CallEventEntity::class,
        MessageEntity::class,
        MessageEventEntity::class,
        ApiKeyEntity::class,
        ProjectEntity::class,
        WebhookEntity::class,
        WebhookDeliveryEntity::class,
        EventEntity::class,
        AuditLogEntity::class,
        ConfigurationEntity::class,
        NetworkSnapshotEntity::class,
        CommandEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class OpenCellDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun callDao(): CallDao
    abstract fun messageDao(): MessageDao
    abstract fun apiKeyDao(): ApiKeyDao
    abstract fun webhookDao(): WebhookDao
    abstract fun eventDao(): EventDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun configurationDao(): ConfigurationDao
    abstract fun commandDao(): CommandDao
}
