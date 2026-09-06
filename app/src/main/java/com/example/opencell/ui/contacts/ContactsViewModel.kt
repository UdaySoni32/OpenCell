package com.example.opencell.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.opencell.domain.model.Contact
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ContactsViewModel : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isAddContactOpen = MutableStateFlow(false)
    val isAddContactOpen: StateFlow<Boolean> = _isAddContactOpen.asStateFlow()

    // Backed by Room via ContactStore: persists across restarts.
    private val _contactList = ContactStore.contacts

    val contacts: StateFlow<List<Contact>> = combine(
        _contactList,
        _searchQuery
    ) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            list.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.phoneNumber.contains(query, ignoreCase = true) ||
                        (it.carrierLabel?.contains(query, ignoreCase = true) == true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
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
}
