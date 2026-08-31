package io.opencell.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.opencell.platform.contacts.Contact
import io.opencell.platform.contacts.ContactEngine
import io.opencell.platform.devices.DeviceEngine
import io.opencell.platform.messaging.MessagingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ComposeUiState(
    val recipient: String = "",
    val messageBody: String = "",
    val isSending: Boolean = false,
    val sentThreadId: String? = null,
    val contactSuggestions: List<Contact> = emptyList(),
    val error: String? = null
) {
    val canSend: Boolean get() = recipient.isNotBlank() && messageBody.isNotBlank() && !isSending
}

@HiltViewModel
class ComposeViewModel @Inject constructor(
    private val messagingEngine: MessagingEngine,
    private val deviceEngine: DeviceEngine,
    private val contactEngine: ContactEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ComposeUiState())
    val uiState: StateFlow<ComposeUiState> = _uiState.asStateFlow()

    fun onRecipientChange(value: String) {
        _uiState.value = _uiState.value.copy(recipient = value)
        if (value.length >= 2) searchContacts(value)
        else _uiState.value = _uiState.value.copy(contactSuggestions = emptyList())
    }

    fun onMessageBodyChange(value: String) {
        _uiState.value = _uiState.value.copy(messageBody = value)
    }

    fun selectContact(contact: Contact) {
        val number = contact.phoneNumbers.firstOrNull()?.number ?: return
        _uiState.value = _uiState.value.copy(
            recipient = number,
            contactSuggestions = emptyList()
        )
    }

    fun send() {
        if (!_uiState.value.canSend) return
        val to = _uiState.value.recipient.trim()
        val body = _uiState.value.messageBody.trim()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            val deviceId = deviceEngine.getLocalDeviceId()
            val result = messagingEngine.sendSms(to = to, body = body, deviceId = deviceId)
            result.fold(
                onSuccess = { message ->
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        sentThreadId = message.threadId,
                        error = null
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        error = e.message ?: "Failed to send"
                    )
                }
            )
        }
    }

    private fun searchContacts(query: String) {
        viewModelScope.launch {
            val contacts = withContext(Dispatchers.IO) {
                contactEngine.searchContacts(query, limit = 5)
            }
            _uiState.value = _uiState.value.copy(contactSuggestions = contacts)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
