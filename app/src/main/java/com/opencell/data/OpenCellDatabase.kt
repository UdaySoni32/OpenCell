package com.opencell.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    entities = [AppCallEntity::class, AuditEntry::class, ApprovalEntity::class, PairedDeviceEntity::class, MemoryFact::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class OpenCellDatabase : RoomDatabase() {
    abstract fun appCallDao(): AppCallDao
    abstract fun memoryDao(): MemoryDao
    abstract fun policyDao(): PolicyDao
    abstract fun pairedDeviceDao(): PairedDeviceDao

    companion object {
        fun create(context: Context): OpenCellDatabase =
            Room.databaseBuilder(context, OpenCellDatabase::class.java, "opencell.db").build()
    }
}
