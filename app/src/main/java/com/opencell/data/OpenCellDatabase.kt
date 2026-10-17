package com.opencell.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.opencell.assistant.AssistantDao
import com.opencell.assistant.AssistantMessage
import com.opencell.assistant.AssistantThread
import com.opencell.calllog.AppCallDao
import com.opencell.calllog.AppCallEntity
import com.opencell.gateway.PairedDeviceDao
import com.opencell.memory.MemoryDao
import com.opencell.memory.MemoryFact
import com.opencell.gateway.PairedDeviceEntity
import com.opencell.policy.ApprovalEntity
import com.opencell.policy.AuditEntry
import com.opencell.policy.PolicyDao

@Database(
    entities = [
        AppCallEntity::class,
        AuditEntry::class,
        ApprovalEntity::class,
        PairedDeviceEntity::class,
        MemoryFact::class,
        AssistantThread::class,
        AssistantMessage::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class OpenCellDatabase : RoomDatabase() {
    abstract fun appCallDao(): AppCallDao
    abstract fun memoryDao(): MemoryDao
    abstract fun policyDao(): PolicyDao
    abstract fun pairedDeviceDao(): PairedDeviceDao
    abstract fun assistantDao(): AssistantDao

    companion object {
        fun create(context: Context): OpenCellDatabase =
            Room.databaseBuilder(context, OpenCellDatabase::class.java, "opencell.db").build()
    }
}
