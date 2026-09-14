package com.example.opencell.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface NavRoute : NavKey {
    @Serializable
    data object Phone : NavRoute

    @Serializable
    data object Messages : NavRoute

    @Serializable
    data object Contacts : NavRoute

    @Serializable
    data object Recents : NavRoute

    @Serializable
    data object Settings : NavRoute

    @Serializable
    data object Developer : NavRoute

    @Serializable
    data class ContactDetails(val contactId: String) : NavRoute {
        constructor(contactId: Long) : this(contactId.toString())
    }

    @Serializable
    data class Conversation(val phoneNumber: String) : NavRoute
}
