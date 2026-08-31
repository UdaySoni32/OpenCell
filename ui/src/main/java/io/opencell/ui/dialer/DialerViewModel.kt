package io.opencell.ui.dialer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.opencell.core.database.entity.CallEntity
import io.opencell.platform.devices.DeviceEngine
import io.opencell.platform.telecom.CallEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DialerUiState(
    val phoneNumber: String = "",
    val isCallInProgress: Boolean = false,
    val callId: String? = null,
    val recentCalls: List<CallEntity> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class DialerViewModel @Inject constructor(
    private val callEngine: CallEngine,
    private val deviceEngine: DeviceEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(DialerUiState())
    val uiState: StateFlow<DialerUiState> = _uiState.asStateFlow()

    init {
        observeRecentCalls()
        observeCurrentCall()
    }

    private fun observeRecentCalls() {
        viewModelScope.launch {
            callEngine.getCallHistory()
                .catch { /* ignore DB errors */ }
                .collect { calls ->
                    _uiState.value = _uiState.value.copy(recentCalls = calls)
                }
        }
    }

    private fun observeCurrentCall() {
        viewModelScope.launch {
            callEngine.currentCall.collect { call ->
                _uiState.value = _uiState.value.copy(
                    isCallInProgress = call != null,
                    callId = call?.id
                )
            }
        }
    }

    fun onPhoneNumberChange(number: String) {
        _uiState.value = _uiState.value.copy(phoneNumber = number)
    }

    fun dial() {
        val number = _uiState.value.phoneNumber
        if (number.isBlank()) return

        viewModelScope.launch {
            val deviceId = deviceEngine.getLocalDeviceId()
            val result = callEngine.makeCall(
                phoneNumber = number,
                deviceId = deviceId
            )
            result.fold(
                onSuccess = { call ->
                    _uiState.value = _uiState.value.copy(
                        isCallInProgress = true,
                        callId = call.id,
                        error = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Failed to place call"
                    )
                }
            )
        }
    }

    fun hangup() {
        val callId = _uiState.value.callId ?: return
        viewModelScope.launch {
            callEngine.hangupCall(callId)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
