package com.example.opencell.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val phoneNumber: String,
    val email: String? = null,
    val avatarColorHex: String = "#6200EE",
    val carrierLabel: String? = null
)
