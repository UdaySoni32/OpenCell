package com.example.opencell.ui.contacts

import android.content.Context
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * App-wide contact store shared by Contacts UI and the gateway (`GET /v1/contacts`).
 * Contacts are persisted in Room via [ContactRepository], surviving process restarts.
 *
 * Automatically loads contacts from [ContactsContract] if permission is granted,
 * falling back to default sample contacts (Alice Smith "+15551234567",
 * Bob Johnson "+15559876543", Emergency "+1911") when device contacts are empty.
 */
object ContactStore {

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    private val _loaded = MutableStateFlow(false)

    /** Current contacts (empty until Room/ContactsContract emits for the first time). */
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    /**
     * Snapshot for synchronous-feeling consumers (gateway route handlers).
     * Waits (bounded) for the first Room load if it has not happened yet.
     */
    suspend fun contactsSnapshot(): List<Contact> {
        if (!_loaded.value) {
            withTimeoutOrNull(5_000) { _loaded.first { it } }
        }
        return _contacts.value
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var repository: ContactRepository? = null

    @Volatile
    private var seeded = false

    /** Idempotent init: safe to call from Application.onCreate(). */
    fun init(repo: ContactRepository, context: Context? = null) {
        this.repository = repo
        repo.getAllContacts()
            .onEach { persisted ->
                if (persisted.isEmpty() && !seeded) {
                    seeded = true
                    scope.launch {
                        val systemContacts = if (context != null) repo.loadContactsFromSystem(context) else emptyList()
                        if (systemContacts.isNotEmpty()) {
                            systemContacts.forEach { repo.addContact(it) }
                        } else {
                            DEFAULT_CONTACTS.forEach { repo.addContact(it) }
                        }
                    }
                    return@onEach
                }
                seeded = true
                _contacts.value = persisted
                _loaded.value = true
            }
            .launchIn(scope)
    }

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

    val DEFAULT_CONTACTS = listOf(
        Contact(
            id = "1",
            name = "Emergency",
            phoneNumber = "+1911",
            email = "emergency@opencell.org",
            carrierLabel = "Priority Carrier"
        ),
        Contact(
            id = "2",
            name = "Alice Smith",
            phoneNumber = "+15551234567",
            email = "alice@opencell.org",
            carrierLabel = "T-Mobile LTE"
        ),
        Contact(
            id = "3",
            name = "Bob Johnson",
            phoneNumber = "+15559876543",
            email = "bob@opencell.org",
            carrierLabel = "Verizon 5G"
        ),
        Contact(
            id = "4",
            name = "Network Operations Center",
            phoneNumber = "+1 (800) 555-0100",
            email = "noc@opencell.net",
            carrierLabel = "LTE / 5G SA"
        ),
        Contact(
            id = "5",
            name = "Cell Tower Tech Support",
            phoneNumber = "+1 (800) 555-0199",
            email = "support@celltowers.org",
            carrierLabel = "Cellular Infra"
        )
    )
}
