package com.example.opencell.ui.developer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.opencell.data.local.preferences.DeveloperPreferencesRepository
import com.example.opencell.data.repository.CallRepository
import com.example.opencell.data.repository.MessageRepository
import com.example.opencell.domain.model.ApiKeyRecord
import com.example.opencell.domain.model.CallRecord
import com.example.opencell.domain.model.CallType
import com.example.opencell.domain.model.MessageRecord
import com.example.opencell.domain.model.ServerLogEntry
import com.example.opencell.domain.model.WebhookRecord
import com.example.opencell.gateway.logging.ServerLogRepository
import com.example.opencell.gateway.service.GatewayServerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class DeveloperViewModel(
    private val developerPreferencesRepository: DeveloperPreferencesRepository,
    private val callRepository: CallRepository,
    private val messageRepository: MessageRepository,
    private val serverLogRepository: ServerLogRepository = ServerLogRepository.instance
) : ViewModel() {

    val developerModeEnabled: StateFlow<Boolean> = developerPreferencesRepository.developerModeEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val apiKey: StateFlow<String> = developerPreferencesRepository.apiKey.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val apiEndpoint: StateFlow<String> = developerPreferencesRepository.apiEndpoint.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "https://opencellid.org/api/v1"
    )

    val mockTelephonyEnabled: StateFlow<Boolean> = developerPreferencesRepository.mockTelephonyEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val isServerRunning: StateFlow<Boolean> = GatewayServerService.isServerRunning

    val gatewayPort: StateFlow<Int> = developerPreferencesRepository.gatewayPort.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 8080
    )

    val apiKeys: StateFlow<List<ApiKeyRecord>> = developerPreferencesRepository.apiKeys.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val webhooks: StateFlow<List<WebhookRecord>> = developerPreferencesRepository.webhooks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val serverLogs: StateFlow<List<ServerLogEntry>> = serverLogRepository.logs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun toggleGatewayServer(context: Context, start: Boolean) {
        viewModelScope.launch {
            developerPreferencesRepository.setGatewayServerEnabled(start)
            if (start) {
                val currentPort = gatewayPort.value
                GatewayServerService.startService(context, currentPort)
                _statusMessage.value = "Gateway Server starting on http://127.0.0.1:$currentPort"
            } else {
                GatewayServerService.stopService(context)
                _statusMessage.value = "Gateway Server stopped"
            }
        }
    }

    fun setGatewayPort(port: Int) {
        viewModelScope.launch {
            developerPreferencesRepository.setGatewayPort(port)
            _statusMessage.value = "Gateway Port set to $port"
        }
    }

    fun generateApiKey(prefix: String, name: String, scopes: List<String>) {
        viewModelScope.launch {
            val keyPrefix = if (prefix == "oc_test_") "oc_test_" else "oc_live_"
            val randomBody = UUID.randomUUID().toString().replace("-", "").take(20)
            val generatedKey = "$keyPrefix$randomBody"

            val record = ApiKeyRecord(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "API Key ${apiKeys.value.size + 1}" },
                key = generatedKey,
                scopes = scopes.ifEmpty { ApiKeyRecord.ALL_SCOPES }
            )

            developerPreferencesRepository.addApiKey(record)
            _statusMessage.value = "Generated new API key: ${record.name}"
        }
    }

    fun revokeApiKey(keyId: String) {
        viewModelScope.launch {
            developerPreferencesRepository.revokeApiKey(keyId)
            _statusMessage.value = "API key revoked"
        }
    }

    fun addWebhook(url: String, events: List<String>, secret: String?) {
        viewModelScope.launch {
            if (url.isBlank()) {
                _statusMessage.value = "Cannot add webhook: URL is empty"
                return@launch
            }
            val webhookSecret = secret?.ifBlank { null }
                ?: "whsec_${UUID.randomUUID().toString().replace("-", "").take(16)}"

            val record = WebhookRecord(
                id = UUID.randomUUID().toString(),
                url = url,
                events = events.ifEmpty { WebhookRecord.ALL_EVENTS },
                secret = webhookSecret
            )

            developerPreferencesRepository.addWebhook(record)
            _statusMessage.value = "Webhook registered: $url"
        }
    }

    fun deleteWebhook(webhookId: String) {
        viewModelScope.launch {
            developerPreferencesRepository.deleteWebhook(webhookId)
            _statusMessage.value = "Webhook unregistered"
        }
    }

    fun clearServerLogs() {
        serverLogRepository.clear()
        _statusMessage.value = "Server logs cleared"
    }

    fun setDeveloperMode(enabled: Boolean) {
        viewModelScope.launch {
            developerPreferencesRepository.setDeveloperModeEnabled(enabled)
            _statusMessage.value = if (enabled) "Developer Mode Activated" else "Developer Mode Deactivated"
        }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch {
            developerPreferencesRepository.setApiKey(key)
            _statusMessage.value = "API Key Saved"
        }
    }

    fun setApiEndpoint(endpoint: String) {
        viewModelScope.launch {
            developerPreferencesRepository.setApiEndpoint(endpoint)
            _statusMessage.value = "API Endpoint Updated"
        }
    }

    fun setMockTelephony(enabled: Boolean) {
        viewModelScope.launch {
            developerPreferencesRepository.setMockTelephonyEnabled(enabled)
            _statusMessage.value = if (enabled) "Mock Telephony Enabled" else "Mock Telephony Disabled"
        }
    }

    fun populateSampleData() {
        viewModelScope.launch {
            val sampleCalls = listOf(
                CallRecord(
                    phoneNumber = "+1 (555) 012-3456",
                    contactName = "Alice Smith",
                    callType = CallType.INCOMING,
                    timestamp = System.currentTimeMillis() - 1800000,
                    durationSeconds = 125
                ),
                CallRecord(
                    phoneNumber = "+1 (800) 555-0100",
                    contactName = "NOC Operations",
                    callType = CallType.OUTGOING,
                    timestamp = System.currentTimeMillis() - 3600000,
                    durationSeconds = 48
                )
            )

            val sampleMessages = listOf(
                MessageRecord(
                    address = "555-0199",
                    contactName = "Cell Tower Ref",
                    body = "OpenCelliD tower location database sync complete. 4 towers mapped in sector.",
                    timestamp = System.currentTimeMillis() - 900000,
                    isIncoming = true
                )
            )

            sampleCalls.forEach { callRepository.insertCall(it) }
            sampleMessages.forEach { messageRepository.insertMessage(it) }

            _statusMessage.value = "Sample DB records populated successfully"
        }
    }

    fun clearDatabase() {
        viewModelScope.launch {
            callRepository.clearAllCalls()
            messageRepository.clearAllMessages()
            _statusMessage.value = "All Room DB records cleared"
        }
    }

    fun resetPreferences() {
        viewModelScope.launch {
            developerPreferencesRepository.clearAll()
            _statusMessage.value = "Developer Preferences reset to defaults"
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    class Factory(
        private val prefsRepository: DeveloperPreferencesRepository,
        private val callRepository: CallRepository,
        private val messageRepository: MessageRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeveloperViewModel(prefsRepository, callRepository, messageRepository) as T
        }
    }
}
