package io.opencell.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.opencell.core.database.entity.EventEntity
import io.opencell.platform.devices.DeviceEngine
import io.opencell.platform.events.EventEngine
import io.opencell.server.api.ApiServer
import io.opencell.server.auth.AuthenticationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import java.net.Inet4Address
import javax.inject.Inject

data class TestingDashboardUiState(
    val isServerRunning: Boolean = false,
    val serverIp: String = "…",
    val serverPort: Int = 8900,
    val recentEvents: List<EventEntity> = emptyList(),
    val generatedApiKey: String? = null,
    val isGeneratingKey: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class TestingDashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiServer: ApiServer,
    private val authService: AuthenticationService,
    private val eventEngine: EventEngine,
    private val deviceEngine: DeviceEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(TestingDashboardUiState())
    val uiState: StateFlow<TestingDashboardUiState> = _uiState.asStateFlow()

    init {
        refreshServerStatus()
        observeRecentEvents()
    }

    fun refreshServerStatus() {
        viewModelScope.launch {
            val isRunning = apiServer.isRunning()
            val ip = withContext(Dispatchers.IO) { getLocalIpAddress() } ?: "0.0.0.0"
            _uiState.value = _uiState.value.copy(
                isServerRunning = isRunning,
                serverIp = ip
            )
        }
    }

    private fun observeRecentEvents() {
        viewModelScope.launch {
            eventEngine.getRecentEvents(limit = 50)
                .catch { /* ignore */ }
                .collect { events ->
                    _uiState.value = _uiState.value.copy(recentEvents = events)
                }
        }
    }

    fun emitTestEvent() {
        viewModelScope.launch {
            val deviceId = deviceEngine.getLocalDeviceId()
            eventEngine.emit(
                name = "test.event",
                deviceId = deviceId,
                data = mapOf(
                    "source" to JsonPrimitive("testing_dashboard"),
                    "message" to JsonPrimitive("Hello from OpenCell!")
                )
            )
            _uiState.value = _uiState.value.copy(message = "Test event emitted!")
        }
    }

    fun generateTestApiKey() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingKey = true)
            try {
                val (rawKey, _) = authService.createApiKey(
                    projectId = "default",
                    name = "Test Key (Dashboard)",
                    scopes = listOf("*")
                )
                _uiState.value = _uiState.value.copy(
                    generatedApiKey = rawKey,
                    isGeneratingKey = false,
                    message = "API key created! Copy it now — it won't be shown again."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingKey = false,
                    message = "Failed to create key: ${e.message}"
                )
            }
        }
    }

    fun dismissGeneratedKey() {
        _uiState.value = _uiState.value.copy(generatedApiKey = null)
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun getLocalIpAddress(): String? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val lp: LinkProperties = cm.getLinkProperties(cm.activeNetwork) ?: return null
            lp.linkAddresses.firstOrNull { address ->
                address.address is Inet4Address && !address.address.isLoopbackAddress
            }?.address?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
