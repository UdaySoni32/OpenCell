package io.opencell.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.opencell.core.model.Message
import io.opencell.platform.devices.DeviceEngine
import io.opencell.platform.messaging.MessagingEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConversationUiState(
    val threadId: String = "",
    val contactAddress: String = "",
    val contactName: String? = null,
    val messages: List<Message> = emptyList(),
    val draftText: String = "",
    val isSending: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messagingEngine: MessagingEngine,
    private val deviceEngine: DeviceEngine
) : ViewModel() {

    private val threadId: String = checkNotNull(savedStateHandle["threadId"])

    private val _uiState = MutableStateFlow(ConversationUiState(threadId = threadId))
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    init {
        loadMessages()
        markSeen()
    }

    private fun loadMessages() {
        viewModelScope.launch {
            messagingEngine.getMessagesByThread(threadId)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
                .collect { messages ->
                    val address = messages.firstOrNull()?.let {
                        if (it.direction.name == "INBOUND") it.sender else it.recipient
                    } ?: ""
                    _uiState.value = _uiState.value.copy(
                        messages = messages,
                        contactAddress = address
                    )
                }
        }
    }

    private fun markSeen() {
        viewModelScope.launch {
            messagingEngine.markSeen(threadId)
        }
    }

    fun onDraftChange(text: String) {
        _uiState.value = _uiState.value.copy(draftText = text)
    }

    fun sendMessage() {
        val text = _uiState.value.draftText.trim()
        val to = _uiState.value.contactAddress
        if (text.isBlank() || to.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, draftText = "")
            val deviceId = deviceEngine.getLocalDeviceId()
            val result = messagingEngine.sendSms(
                to = to,
                body = text,
                deviceId = deviceId
            )
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isSending = false, error = null)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        error = e.message ?: "Failed to send message",
                        draftText = text // restore draft on failure
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
