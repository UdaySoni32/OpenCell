package io.opencell.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.opencell.core.database.dao.ApiKeyDao
import io.opencell.core.database.entity.ApiKeyEntity
import io.opencell.server.auth.AuthenticationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApiKeysUiState(
    val apiKeys: List<ApiKeyEntity> = emptyList(),
    val isLoading: Boolean = false,
    val showCreateDialog: Boolean = false,
    val newKeyName: String = "",
    val newKeyValue: String? = null, // Only shown once after creation
    val error: String? = null
)

@HiltViewModel
class ApiKeysViewModel @Inject constructor(
    private val authService: AuthenticationService,
    private val apiKeyDao: ApiKeyDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiKeysUiState())
    val uiState: StateFlow<ApiKeysUiState> = _uiState.asStateFlow()

    // We need the project ID to list keys. Use a fixed "default" for now.
    private val defaultProjectId = "default"

    init {
        observeApiKeys()
    }

    private fun observeApiKeys() {
        viewModelScope.launch {
            apiKeyDao.getActiveKeysForProject(defaultProjectId)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(error = e.message)
                }
                .collect { keys ->
                    _uiState.value = _uiState.value.copy(
                        apiKeys = keys,
                        isLoading = false
                    )
                }
        }
    }

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true)
    }

    fun dismissCreateDialog() {
        _uiState.value = _uiState.value.copy(
            showCreateDialog = false,
            newKeyName = ""
        )
    }

    fun onKeyNameChange(name: String) {
        _uiState.value = _uiState.value.copy(newKeyName = name)
    }

    fun createApiKey() {
        val name = _uiState.value.newKeyName.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            try {
                val (rawKey, _) = authService.createApiKey(
                    projectId = defaultProjectId,
                    name = name,
                    scopes = listOf("*")
                )
                _uiState.value = _uiState.value.copy(
                    showCreateDialog = false,
                    newKeyName = "",
                    newKeyValue = rawKey,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to create API key"
                )
            }
        }
    }

    fun revokeApiKey(keyId: String) {
        viewModelScope.launch {
            authService.revokeApiKey(keyId)
        }
    }

    fun dismissNewKeyValue() {
        _uiState.value = _uiState.value.copy(newKeyValue = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
