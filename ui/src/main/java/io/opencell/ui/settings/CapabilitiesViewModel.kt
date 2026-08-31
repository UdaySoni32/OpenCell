package io.opencell.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.opencell.core.model.Capability
import io.opencell.platform.capabilities.CapabilityEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CapabilitiesUiState(
    val capabilities: List<Capability> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class CapabilitiesViewModel @Inject constructor(
    private val capabilityEngine: CapabilityEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(CapabilitiesUiState())
    val uiState: StateFlow<CapabilitiesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val capabilities = capabilityEngine.getAllCapabilities()
                _uiState.value = _uiState.value.copy(
                    capabilities = capabilities,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load capabilities"
                )
            }
        }
    }
}
