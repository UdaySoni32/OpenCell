package com.example.opencell.ui.phone

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.opencell.data.repository.CallRepository
import com.example.opencell.data.repository.SpeedDialStore
import com.example.opencell.domain.model.AggregatedCallRecord
import com.example.opencell.domain.model.CallRecord
import com.example.opencell.domain.model.CallSession
import com.example.opencell.domain.model.CallType
import com.example.opencell.domain.model.Contact
import com.example.opencell.domain.model.SpeedDialEntry
import com.example.opencell.domain.model.aggregateConsecutiveCalls
import com.example.opencell.telecom.CallEngine
import com.example.opencell.telecom.DefaultDialerManager
import com.example.opencell.ui.contacts.ContactStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PhoneViewModel(
    private val callRepository: CallRepository,
    private val callEngine: CallEngine,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _dialedNumber = MutableStateFlow("")
    val dialedNumber: StateFlow<String> = _dialedNumber.asStateFlow()

    private val _isDialpadExpanded = MutableStateFlow(false)
    val isDialpadExpanded: StateFlow<Boolean> = _isDialpadExpanded.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow<CallType?>(null) // null = ALL
    val selectedFilter: StateFlow<CallType?> = _selectedFilter.asStateFlow()

    @OptIn(FlowPreview::class)
    private val debouncedSearchQuery = _searchQuery.debounce { if (it.isEmpty()) 0L else 300L }

    val activeCallSession: StateFlow<CallSession?> = callEngine.activeCallSession
    val isModemAvailable: StateFlow<Boolean> = callEngine.isModemAvailable

    val speedDialContacts: StateFlow<List<Contact>> = ContactStore.contacts

    val speedDialEntries: StateFlow<List<SpeedDialEntry>> = SpeedDialStore.speedDialEntries
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentCalls: StateFlow<List<CallRecord>> = callRepository.getAllCalls()
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val aggregatedRecentCalls: StateFlow<List<AggregatedCallRecord>> = combine(
        callRepository.getAllCalls(),
        debouncedSearchQuery,
        _selectedFilter
    ) { allCalls, query, filter ->
        val filteredByFilter = if (filter == null) allCalls else allCalls.filter { it.callType == filter }
        val filteredByQuery = if (query.isBlank()) {
            filteredByFilter
        } else {
            filteredByFilter.filter {
                it.phoneNumber.contains(query, ignoreCase = true) ||
                        (it.contactName?.contains(query, ignoreCase = true) == true)
            }
        }
        filteredByQuery.aggregateConsecutiveCalls()
    }
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isDefaultDialer = MutableStateFlow(true)
    val isDefaultDialer: StateFlow<Boolean> = _isDefaultDialer.asStateFlow()

    fun refreshDefaultDialerStatus(context: Context) {
        _isDefaultDialer.value = DefaultDialerManager.isDefaultDialer(context)
    }

    fun getSetDefaultDialerIntent(context: Context): Intent {
        return DefaultDialerManager.createRequestDefaultDialerIntent(context)
    }

    private val _localActionStatus = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = combine(
        callEngine.statusMessage,
        _localActionStatus
    ) { engineMsg, localMsg ->
        localMsg ?: engineMsg
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

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

    fun toggleDialpad() {
        _isDialpadExpanded.value = !_isDialpadExpanded.value
    }

    fun setDialpadExpanded(expanded: Boolean) {
        _isDialpadExpanded.value = expanded
    }

    fun onDigitClick(digit: String) {
        if (!_isDialpadExpanded.value) {
            _isDialpadExpanded.value = true
        }
        if (activeCallSession.value != null) {
            val char = digit.firstOrNull()
            if (char != null) {
                callEngine.sendDtmf(char)
            }
        }
        if (_dialedNumber.value.length < 20) {
            _dialedNumber.value += digit
        }
    }

    fun onBackspaceClick() {
        if (_dialedNumber.value.isNotEmpty()) {
            _dialedNumber.value = _dialedNumber.value.dropLast(1)
        }
    }

    fun onClearClick() {
        _dialedNumber.value = ""
    }

    fun onCallClick() {
        val number = _dialedNumber.value.ifBlank { "Unknown" }
        callEngine.initiateCall(number)
        _dialedNumber.value = ""
        _isDialpadExpanded.value = false
    }

    fun callNumber(number: String) {
        if (number.isBlank()) return
        callEngine.initiateCall(number)
    }

    fun simulateIncomingCall(number: String? = null) {
        val target = number?.ifBlank { null } ?: _dialedNumber.value.ifBlank { "+15551234567" }
        callEngine.simulateIncomingCall(target)
    }

    fun setDialedNumber(number: String) {
        _dialedNumber.value = number.take(40)
        if (number.isNotBlank()) {
            _isDialpadExpanded.value = true
        }
    }

    fun addContact(name: String, phone: String, carrier: String?) {
        viewModelScope.launch {
            ContactStore.add(name, phone, carrier)
        }
    }

    fun assignSpeedDial(slot: Int, phoneNumber: String) {
        SpeedDialStore.assignSlot(slot, phoneNumber)
    }

    fun removeSpeedDial(slot: Int) {
        SpeedDialStore.removeSlot(slot)
    }

    fun answerCall() {
        callEngine.answerCall()
    }

    fun rejectCall() {
        callEngine.rejectCall()
    }

    fun hangupCall() {
        callEngine.hangupCall()
    }

    fun toggleMute() {
        callEngine.toggleMute()
    }

    fun toggleSpeaker() {
        callEngine.toggleSpeaker()
    }

    fun sendDtmf(digit: Char) {
        callEngine.sendDtmf(digit)
    }

    fun stopDtmf() {
        callEngine.stopDtmf()
    }

    fun clearStatus() {
        _localActionStatus.value = null
        callEngine.clearStatusMessage()
    }

    class Factory(
        private val repository: CallRepository,
        private val callEngine: CallEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PhoneViewModel(repository, callEngine) as T
        }
    }
}
