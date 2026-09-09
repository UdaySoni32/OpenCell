package com.example.opencell.ui.phone

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.opencell.data.repository.CallRepository
import com.example.opencell.domain.model.CallRecord
import com.example.opencell.domain.model.CallSession
import com.example.opencell.domain.model.Contact
import com.example.opencell.telecom.CallEngine
import com.example.opencell.telecom.DefaultDialerManager
import com.example.opencell.ui.contacts.ContactStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PhoneViewModel(
    private val callRepository: CallRepository,
    private val callEngine: CallEngine
) : ViewModel() {

    private val _dialedNumber = MutableStateFlow("")
    val dialedNumber: StateFlow<String> = _dialedNumber.asStateFlow()

    private val _isDialpadExpanded = MutableStateFlow(false)
    val isDialpadExpanded: StateFlow<Boolean> = _isDialpadExpanded.asStateFlow()

    val activeCallSession: StateFlow<CallSession?> = callEngine.activeCallSession
    val isModemAvailable: StateFlow<Boolean> = callEngine.isModemAvailable

    val speedDialContacts: StateFlow<List<Contact>> = ContactStore.contacts

    val recentCalls: StateFlow<List<CallRecord>> = callRepository.getAllCalls()
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

    /**
     * Places a call to an explicit number (redial from Recents, call button
     * in Contacts) without going through the dialpad state.
     */
    fun callNumber(number: String) {
        if (number.isBlank()) return
        callEngine.initiateCall(number)
    }

    /**
     * Triggers a simulated incoming call for instant testing.
     */
    fun simulateIncomingCall(number: String? = null) {
        val target = number?.ifBlank { null } ?: _dialedNumber.value.ifBlank { "+15551234567" }
        callEngine.simulateIncomingCall(target)
    }

    /**
     * Replaces the dial pad number (used when handling external ACTION_DIAL /
     * ACTION_VIEW tel: intents routed to OpenCell as the default dialer).
     */
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
