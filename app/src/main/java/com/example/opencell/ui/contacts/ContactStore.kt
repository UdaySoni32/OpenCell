package com.example.opencell.ui.contacts

import com.example.opencell.domain.model.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide contact store shared by the Contacts UI and the gateway
 * (`GET /v1/contacts`). Previously the gateway built a throwaway
 * [ContactsViewModel], whose flow never loads outside composition, so the
 * API always returned an empty list; and the UI's in-memory contacts were
 * lost whenever the process died. Both now read and write this store.
 *
 * Ships with the same seed contacts the UI previously hard-coded.
 */
object ContactStore {

    private val _contacts = MutableStateFlow(
        listOf(
            Contact(
                id = "1",
                name = "Emergency Services",
                phoneNumber = "911",
                email = "emergency@opencell.org",
                carrierLabel = "Priority Carrier"
            ),
            Contact(
                id = "2",
                name = "Network Operations Center",
                phoneNumber = "+1 (800) 555-0100",
                email = "noc@opencell.net",
                carrierLabel = "LTE / 5G SA"
            ),
            Contact(
                id = "3",
                name = "Cell Tower Tech Support",
                phoneNumber = "+1 (800) 555-0199",
                email = "support@celltowers.org",
                carrierLabel = "Cellular Infra"
            ),
            Contact(
                id = "4",
                name = "Alice Smith",
                phoneNumber = "+1 (555) 012-3456",
                carrierLabel = "T-Mobile"
            ),
            Contact(
                id = "5",
                name = "Bob Johnson",
                phoneNumber = "+1 (555) 019-8765",
                carrierLabel = "Verizon 5G"
            )
        )
    )

    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    fun add(name: String, phone: String, carrier: String?): Contact {
        val newContact = Contact(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            phoneNumber = phone,
            carrierLabel = carrier?.ifBlank { null }
        )
        _contacts.value = _contacts.value + newContact
        return newContact
    }

    fun delete(contactId: String) {
        _contacts.value = _contacts.value.filter { it.id != contactId }
    }
}
