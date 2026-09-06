package com.example.opencell.ui.contacts

import com.example.opencell.data.repository.ContactRepository
import com.example.opencell.domain.model.Contact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * App-wide contact store shared by the Contacts UI and the gateway
 * (`GET /v1/contacts`). Contacts are persisted in Room via [ContactRepository],
 * so they survive process death and app restarts.
 *
 * On first launch (empty DB) the default seed contacts are inserted.
 */
object ContactStore {

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _loaded = MutableStateFlow(false)

    /** Current contacts (empty until the DB emits for the first time). */
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    /**
     * Snapshot for synchronous-feeling consumers (gateway route handlers).
     * Waits (bounded) for the first Room load if it has not happened yet,
     * so an early request does not observe an empty list.
     */
    suspend fun contactsSnapshot(): List<Contact> {
        if (!_loaded.value) {
            withTimeoutOrNull(5_000) { _loaded.first { it } }
        }
        return _contacts.value
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Idempotent init: safe to call from Application.onCreate(). */
    fun init(repository: ContactRepository) {
        repository.getAllContacts()
            .onEach { persisted ->
                // Seed the default contacts once if the database is empty.
                if (persisted.isEmpty() && !seeded) {
                    seeded = true
                    DEFAULT_CONTACTS.forEach { repository.addContact(it) }
                    return@onEach // next emission from Room will contain them
                }
                seeded = true
                _contacts.value = persisted
                _loaded.value = true
            }
            .launchIn(scope)
    }

    @Volatile
    private var seeded = false

    suspend fun add(name: String, phone: String, carrier: String?): Contact {
        val newContact = Contact(
            id = UUID.randomUUID().toString(),
            name = name,
            phoneNumber = phone,
            carrierLabel = carrier?.ifBlank { null }
        )
        repository?.addContact(newContact)
        return newContact
    }

    suspend fun delete(contactId: String) {
        val contact = _contacts.value.find { it.id == contactId } ?: return
        repository?.deleteContact(contact)
    }

    @Volatile
    private var repository: ContactRepository? = null

    private val DEFAULT_CONTACTS = listOf(
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
}
