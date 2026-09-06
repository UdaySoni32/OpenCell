package com.example.opencell.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "message_records")
data class MessageRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val address: String,
    val contactName: String? = null,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isIncoming: Boolean = true,
    val isRead: Boolean = true,
    val status: String = "RECEIVED",
    val threadId: Long = 0L
)
