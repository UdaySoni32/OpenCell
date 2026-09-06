package com.example.opencell.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.opencell.data.repository.MessageRepository
import com.example.opencell.domain.model.MessageRecord
import com.example.opencell.messaging.MessageEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MessagesViewModel(
    private val messageRepository: MessageRepository,
    private val messageEngine: MessageEngine
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isComposeOpen = MutableStateFlow(false)
    val isComposeOpen: StateFlow<Boolean> = _isComposeOpen.asStateFlow()

    val statusMessage: StateFlow<String?> = messageEngine.statusMessage
    val isSmsCapable: StateFlow<Boolean> = messageEngine.isSmsCapable

    val messages: StateFlow<List<MessageRecord>> = combine(
        messageRepository.getAllMessages(),
        _searchQuery
    ) { allMessages, query ->
        if (query.isBlank()) {
            allMessages
        } else {
            allMessages.filter {
                it.address.contains(query, ignoreCase = true) ||
                        it.body.contains(query, ignoreCase = true) ||
                        (it.contactName?.contains(query, ignoreCase = true) == true)
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

    fun openCompose() {
        _isComposeOpen.value = true
    }

    fun closeCompose() {
        _isComposeOpen.value = false
    }

    fun sendMessage(recipient: String, body: String) {
        if (recipient.isBlank() || body.isBlank()) return
        messageEngine.sendMessage(recipient, body)
        _isComposeOpen.value = false
    }

    fun deleteMessage(message: MessageRecord) {
        viewModelScope.launch {
            messageRepository.deleteMessage(message)
        }
    }

    fun clearAllMessages() {
        viewModelScope.launch {
            messageRepository.clearAllMessages()
        }
    }

    fun clearStatus() {
        messageEngine.clearStatusMessage()
    }

    class Factory(
        private val repository: MessageRepository,
        private val messageEngine: MessageEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MessagesViewModel(repository, messageEngine) as T
        }
    }
}
