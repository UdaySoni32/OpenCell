package io.opencell.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.opencell.platform.contacts.Contact
import io.opencell.platform.contacts.ContactEngine
import io.opencell.platform.devices.DeviceEngine
import io.opencell.platform.telecom.CallEngine
import io.opencell.ui.dialer.CallDisplayInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ContactDisplayInfo(
    val contact: Contact
)

data class HomeUiState(
    val contacts: List<ContactDisplayInfo> = emptyList(),
    val recentCalls: List<CallDisplayInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val callEngine: CallEngine,
    private val deviceEngine: DeviceEngine,
    private val contactEngine: ContactEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadContacts()
        observeRecentCalls()
    }

    private fun loadContacts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val contacts = withContext(Dispatchers.IO) {
                contactEngine.getAllContacts(limit = 20)
            }
            _uiState.value = _uiState.value.copy(
                contacts = contacts.map { ContactDisplayInfo(it) },
                isLoading = false
            )
        }
    }

    private fun observeRecentCalls() {
        viewModelScope.launch {
            callEngine.getCallHistory()
                .catch { /* ignore DB errors */ }
                .collect { calls ->
                    val displayCalls = withContext(Dispatchers.IO) {
                        calls.take(50).map { call ->
                            val number = if (call.direction == "INBOUND") call.fromNumber else call.toNumber
                            val name = if (number.isNotBlank()) {
                                contactEngine.lookupByPhoneNumber(number)?.displayName
                            } else null
                            CallDisplayInfo(call = call, contactName = name)
                        }
                    }
                    _uiState.value = _uiState.value.copy(recentCalls = displayCalls)
                }
        }
    }
}
