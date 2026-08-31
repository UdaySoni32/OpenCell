package io.opencell.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.opencell.core.model.Conversation
import io.opencell.platform.devices.DeviceEngine
import io.opencell.platform.messaging.MessagingEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MessagesUiState(
    val conversations: List<Conversation> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val filteredConversations: List<Conversation>
        get() = if (searchQuery.isBlank()) conversations
        else conversations.filter {
            it.contactAddress.contains(searchQuery, ignoreCase = true) ||
            it.contactName?.contains(searchQuery, ignoreCase = true) == true ||
            it.lastMessage.contains(searchQuery, ignoreCase = true)
        }
}

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val messagingEngine: MessagingEngine,
    private val deviceEngine: DeviceEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState.asStateFlow()

    init {
        loadConversations()
    }

    private fun loadConversations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            messagingEngine.getConversations()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
                .collect { conversations ->
                    _uiState.value = _uiState.value.copy(
                        conversations = conversations,
                        isLoading = false
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
