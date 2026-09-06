package com.example.opencell.ui.recents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.opencell.data.repository.CallRepository
import com.example.opencell.domain.model.CallRecord
import com.example.opencell.domain.model.CallType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecentsViewModel(
    private val callRepository: CallRepository
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow<CallType?>(null) // null = ALL
    val selectedFilter: StateFlow<CallType?> = _selectedFilter.asStateFlow()

    val calls: StateFlow<List<CallRecord>> = combine(
        callRepository.getAllCalls(),
        _selectedFilter
    ) { allCalls, filter ->
        if (filter == null) {
            allCalls
        } else {
            allCalls.filter { it.callType == filter }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setFilter(filter: CallType?) {
        _selectedFilter.value = filter
    }

    fun deleteCall(call: CallRecord) {
        viewModelScope.launch {
            callRepository.deleteCall(call)
        }
    }

    fun clearAllCalls() {
        viewModelScope.launch {
            callRepository.clearAllCalls()
        }
    }

    class Factory(private val repository: CallRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecentsViewModel(repository) as T
        }
    }
}
