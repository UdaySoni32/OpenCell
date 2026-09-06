package com.example.opencell.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.opencell.data.local.db.dao.CallRecordDao
import com.example.opencell.data.local.db.dao.MessageRecordDao
import com.example.opencell.data.local.db.entity.CallRecordEntity
import com.example.opencell.data.local.db.entity.MessageRecordEntity

@Database(
    entities = [CallRecordEntity::class, MessageRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callRecordDao(): CallRecordDao
    abstract fun messageRecordDao(): MessageRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "opencell_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
