package com.example.opencell.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiKeyRecord(
    val id: String,
    val name: String,
    val key: String,
    val scopes: List<String>,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SCOPE_CALLS_READ = "calls:read"
        const val SCOPE_CALLS_CREATE = "calls:create"
        const val SCOPE_CALLS_CONTROL = "calls:control"
        const val SCOPE_MESSAGES_READ = "messages:read"
        const val SCOPE_MESSAGES_CREATE = "messages:create"
        const val SCOPE_CONTACTS_READ = "contacts:read"
        const val SCOPE_SIM_READ = "sim:read"
        const val SCOPE_NETWORK_READ = "network:read"
        const val SCOPE_EVENTS_READ = "events:read"

        val ALL_SCOPES = listOf(
            SCOPE_CALLS_READ,
            SCOPE_CALLS_CREATE,
            SCOPE_CALLS_CONTROL,
            SCOPE_MESSAGES_READ,
            SCOPE_MESSAGES_CREATE,
            SCOPE_CONTACTS_READ,
            SCOPE_SIM_READ,
            SCOPE_NETWORK_READ,
            SCOPE_EVENTS_READ
        )
    }
}
