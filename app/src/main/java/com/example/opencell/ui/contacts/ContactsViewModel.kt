package com.example.opencell.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.opencell.domain.model.Contact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ContactsViewModel(
    contactSource: Flow<List<Contact>> = ContactStore.contacts
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isAddContactOpen = MutableStateFlow(false)
    val isAddContactOpen: StateFlow<Boolean> = _isAddContactOpen.asStateFlow()

    val contacts: StateFlow<List<Contact>> = combine(
        contactSource,
        _searchQuery
    ) { list, query ->
        val deduplicated = list.distinctBy { contact ->
            val cleanName = contact.name.trim()
            val normPhone = contact.phoneNumber.replace(Regex("[^0-9]"), "").takeLast(10)
            if (normPhone.isNotEmpty()) {
                normPhone
            } else if (cleanName.isNotEmpty()) {
                cleanName.lowercase()
            } else {
                contact.id
            }
        }.map { contact ->
            if (contact.name != contact.name.trim()) {
                contact.copy(name = contact.name.trim())
            } else {
                contact
            }
        }
        val filtered = if (query.isBlank()) {
            deduplicated
        } else {
            deduplicated.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.phoneNumber.contains(query, ignoreCase = true) ||
                        (it.carrierLabel?.contains(query, ignoreCase = true) == true)
            }
        }
        filtered.sortedWith(contactComparator)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val groupedContacts: StateFlow<Map<String, List<Contact>>> = contacts
        .map { list -> list.groupBy { getSectionHeader(it) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun openAddContact() {
        _isAddContactOpen.value = true
    }

    fun closeAddContact() {
        _isAddContactOpen.value = false
    }

    fun addContact(name: String, phone: String, carrier: String?) {
        if (name.isBlank() || phone.isBlank()) return
        viewModelScope.launch {
            ContactStore.add(name, phone, carrier)
            _isAddContactOpen.value = false
        }
    }

    fun deleteContact(contact: Contact) {
        viewModelScope.launch {
            ContactStore.delete(contact.id)
        }
    }

    companion object {
        fun getSectionHeader(contact: Contact): String {
            val displayName = contact.name.ifBlank { contact.phoneNumber }.trim()
            if (displayName.isEmpty()) return "#"
            val firstChar = displayName.first()
            return if (firstChar.uppercaseChar() in 'A'..'Z') {
                firstChar.uppercaseChar().toString()
            } else {
                "#"
            }
        }

        private fun getSectionPriority(sectionHeader: String): Int {
            return if (sectionHeader.length == 1 && sectionHeader[0] in 'A'..'Z') {
                sectionHeader[0] - 'A'
            } else {
                100
            }
        }

        val contactComparator = Comparator<Contact> { c1, c2 ->
            val header1 = getSectionHeader(c1)
            val header2 = getSectionHeader(c2)
            val p1 = getSectionPriority(header1)
            val p2 = getSectionPriority(header2)

            if (p1 != p2) {
                p1.compareTo(p2)
            } else {
                val displayName1 = c1.name.ifBlank { c1.phoneNumber }.trim()
                val displayName2 = c2.name.ifBlank { c2.phoneNumber }.trim()
                val nameCompare = displayName1.compareTo(displayName2, ignoreCase = true)
                if (nameCompare != 0) {
                    nameCompare
                } else {
                    val exactCompare = displayName1.compareTo(displayName2)
                    if (exactCompare != 0) exactCompare else c1.id.compareTo(c2.id)
                }
            }
        }
    }
}
