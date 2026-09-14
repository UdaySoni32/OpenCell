package com.example.opencell.ui.recents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.opencell.data.repository.CallRepository
import com.example.opencell.domain.model.AggregatedCallRecord
import com.example.opencell.domain.model.CallRecord
import com.example.opencell.domain.model.CallType
import com.example.opencell.domain.model.aggregateConsecutiveCalls
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecentsViewModel(
    private val callRepository: CallRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow<CallType?>(null) // null = ALL
    val selectedFilter: StateFlow<CallType?> = _selectedFilter.asStateFlow()

    private val _isDialerOpen = MutableStateFlow(false)
    val isDialerOpen: StateFlow<Boolean> = _isDialerOpen.asStateFlow()

    /** Opens the in-sheet dialpad on the Recents home screen. */
    fun openDialer() {
        _isDialerOpen.value = true
    }

    fun closeDialer() {
        _isDialerOpen.value = false
    }

    val calls: StateFlow<List<AggregatedCallRecord>> = combine(
        callRepository.getAllCalls(),
        _selectedFilter
    ) { allCalls, filter ->
        val filtered = if (filter == null) {
            allCalls
        } else {
            allCalls.filter { it.callType == filter }
        }
        filtered.aggregateConsecutiveCalls()
    }
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setFilter(filter: CallType?) {
        _selectedFilter.value = filter
    }

    fun deleteCall(call: AggregatedCallRecord) {
        viewModelScope.launch {
            call.calls.forEach { callRepository.deleteCall(it) }
        }
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
