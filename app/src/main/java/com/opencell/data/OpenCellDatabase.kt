package com.opencell.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.opencell.calllog.AppCallDao
import com.opencell.calllog.AppCallEntity

@Database(entities = [AppCallEntity::class], version = 1, exportSchema = true)
abstract class OpenCellDatabase : RoomDatabase() {
    abstract fun appCallDao(): AppCallDao

    companion object {
        fun create(context: Context): OpenCellDatabase =
            Room.databaseBuilder(context, OpenCellDatabase::class.java, "opencell.db").build()
    }
}
