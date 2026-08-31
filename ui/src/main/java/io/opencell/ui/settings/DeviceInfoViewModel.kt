package io.opencell.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.opencell.core.database.entity.DeviceEntity
import io.opencell.core.model.NetworkInfo
import io.opencell.core.model.SimInfo
import io.opencell.platform.devices.DeviceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DeviceInfoUiState(
    val device: DeviceEntity? = null,
    val simInfoList: List<SimInfo> = emptyList(),
    val networkInfo: NetworkInfo? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class DeviceInfoViewModel @Inject constructor(
    private val deviceEngine: DeviceEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceInfoUiState())
    val uiState: StateFlow<DeviceInfoUiState> = _uiState.asStateFlow()

    init {
        loadDeviceInfo()
    }

    fun loadDeviceInfo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val device = deviceEngine.getOrCreateLocalDevice()
                val simInfoList = withContext(Dispatchers.IO) {
                    deviceEngine.getSimInfo()
                }
                val networkInfo = withContext(Dispatchers.IO) {
                    deviceEngine.getNetworkInfo()
                }
                _uiState.value = _uiState.value.copy(
                    device = device,
                    simInfoList = simInfoList,
                    networkInfo = networkInfo,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load device info"
                )
            }
        }
    }
}
