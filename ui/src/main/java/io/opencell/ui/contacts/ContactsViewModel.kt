package io.opencell.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.opencell.platform.contacts.Contact
import io.opencell.platform.contacts.ContactEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ContactsUiState(
    val contacts: List<Contact> = emptyList(),
    val starredIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val filteredContacts: List<Contact>
        get() = if (searchQuery.isBlank()) contacts
        else contacts.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
            it.phoneNumbers.any { p -> p.number.contains(searchQuery) }
        }
}

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactEngine: ContactEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    init {
        loadContacts()
    }

    fun loadContacts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val contacts = withContext(Dispatchers.IO) {
                    val all = contactEngine.getAllContacts(limit = 500)
                    val starred = contactEngine.getStarredContacts(limit = 100)
                    val starredIds = starred.map { it.id }.toSet()

                    // Sort: starred first, then A-Z, unknown/blank names last
                    all.sortedWith(compareBy<Contact> { it.id !in starredIds }
                        .thenBy { it.displayName.firstOrNull()?.let { c ->
                            if (c.isLetter()) c.uppercaseChar() else null
                        } ?: Char.MAX_VALUE }
                        .thenBy { it.displayName })
                }
                _uiState.value = _uiState.value.copy(
                    contacts = contacts,
                    starredIds = withContext(Dispatchers.IO) {
                        contactEngine.getStarredContacts(limit = 100).map { it.id }.toSet()
                    },
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load contacts"
                )
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
