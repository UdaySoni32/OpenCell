package com.example.opencell.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val email: String? = null,
    val avatarColorHex: String = "#6200EE",
    val carrierLabel: String? = null
)
