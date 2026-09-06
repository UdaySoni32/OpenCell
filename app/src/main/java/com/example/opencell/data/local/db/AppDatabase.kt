package com.example.opencell.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.opencell.data.local.db.dao.CallRecordDao
import com.example.opencell.data.local.db.dao.ContactDao
import com.example.opencell.data.local.db.dao.MessageRecordDao
import com.example.opencell.data.local.db.entity.CallRecordEntity
import com.example.opencell.data.local.db.entity.ContactEntity
import com.example.opencell.data.local.db.entity.MessageRecordEntity

@Database(
    entities = [CallRecordEntity::class, MessageRecordEntity::class, ContactEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callRecordDao(): CallRecordDao
    abstract fun messageRecordDao(): MessageRecordDao
    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1 -> v2: add the contacts table (preserves existing call/message data). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `contacts` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `phoneNumber` TEXT NOT NULL,
                        `email` TEXT,
                        `avatarColorHex` TEXT NOT NULL,
                        `carrierLabel` TEXT,
                        PRIMARY KEY(`id`)
                    )"""
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "opencell_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
