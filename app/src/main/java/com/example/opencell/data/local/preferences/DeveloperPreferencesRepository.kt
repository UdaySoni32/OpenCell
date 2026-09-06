package com.example.opencell.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.opencell.domain.model.ApiKeyRecord
import com.example.opencell.domain.model.WebhookRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.developerDataStore: DataStore<Preferences> by preferencesDataStore(name = "developer_preferences")

interface DeveloperPreferencesRepository {
    val developerModeEnabled: Flow<Boolean>
    val apiKey: Flow<String>
    val apiEndpoint: Flow<String>
    val mockTelephonyEnabled: Flow<Boolean>
    val gatewayServerEnabled: Flow<Boolean>
    val gatewayPort: Flow<Int>
    val apiKeys: Flow<List<ApiKeyRecord>>
    val webhooks: Flow<List<WebhookRecord>>

    suspend fun setDeveloperModeEnabled(enabled: Boolean)
    suspend fun setApiKey(key: String)
    suspend fun setApiEndpoint(endpoint: String)
    suspend fun setMockTelephonyEnabled(enabled: Boolean)
    suspend fun setGatewayServerEnabled(enabled: Boolean)
    suspend fun setGatewayPort(port: Int)
    suspend fun addApiKey(keyRecord: ApiKeyRecord)
    suspend fun revokeApiKey(keyId: String)
    suspend fun addWebhook(webhookRecord: WebhookRecord)
    suspend fun deleteWebhook(webhookId: String)
    suspend fun clearAll()
}

class DeveloperPreferencesRepositoryImpl(
    private val context: Context
) : DeveloperPreferencesRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val DEVELOPER_MODE_ENABLED = booleanPreferencesKey("developer_mode_enabled")
        val API_KEY = stringPreferencesKey("api_key")
        val API_ENDPOINT = stringPreferencesKey("api_endpoint")
        val MOCK_TELEPHONY_ENABLED = booleanPreferencesKey("mock_telephony_enabled")
        val GATEWAY_SERVER_ENABLED = booleanPreferencesKey("gateway_server_enabled")
        val GATEWAY_PORT = intPreferencesKey("gateway_port")
        val API_KEYS_JSON = stringPreferencesKey("api_keys_json")
        val WEBHOOKS_JSON = stringPreferencesKey("webhooks_json")
    }

    private val defaultMasterKey = ApiKeyRecord(
        id = "default_master",
        name = "Default Master Key",
        key = "oc_live_opencell_default_key_999",
        scopes = ApiKeyRecord.ALL_SCOPES
    )

    override val developerModeEnabled: Flow<Boolean> = context.developerDataStore.data.map { preferences ->
        preferences[Keys.DEVELOPER_MODE_ENABLED] ?: false
    }

    override val apiKey: Flow<String> = context.developerDataStore.data.map { preferences ->
        preferences[Keys.API_KEY] ?: ""
    }

    override val apiEndpoint: Flow<String> = context.developerDataStore.data.map { preferences ->
        preferences[Keys.API_ENDPOINT] ?: "https://opencellid.org/api/v1"
    }

    override val mockTelephonyEnabled: Flow<Boolean> = context.developerDataStore.data.map { preferences ->
        preferences[Keys.MOCK_TELEPHONY_ENABLED] ?: true
    }

    override val gatewayServerEnabled: Flow<Boolean> = context.developerDataStore.data.map { preferences ->
        preferences[Keys.GATEWAY_SERVER_ENABLED] ?: false
    }

    override val gatewayPort: Flow<Int> = context.developerDataStore.data.map { preferences ->
        preferences[Keys.GATEWAY_PORT] ?: 8080
    }

    override val apiKeys: Flow<List<ApiKeyRecord>> = context.developerDataStore.data.map { preferences ->
        val rawJson = preferences[Keys.API_KEYS_JSON]
        if (rawJson.isNullOrBlank()) {
            listOf(defaultMasterKey)
        } else {
            try {
                val parsed = json.decodeFromString<List<ApiKeyRecord>>(rawJson)
                if (parsed.isEmpty()) listOf(defaultMasterKey) else parsed
            } catch (_: Exception) {
                listOf(defaultMasterKey)
            }
        }
    }

    override val webhooks: Flow<List<WebhookRecord>> = context.developerDataStore.data.map { preferences ->
        val rawJson = preferences[Keys.WEBHOOKS_JSON]
        if (rawJson.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                json.decodeFromString<List<WebhookRecord>>(rawJson!!)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    override suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        context.developerDataStore.edit { preferences ->
            preferences[Keys.DEVELOPER_MODE_ENABLED] = enabled
        }
    }

    override suspend fun setApiKey(key: String) {
        context.developerDataStore.edit { preferences ->
            preferences[Keys.API_KEY] = key
        }
    }

    override suspend fun setApiEndpoint(endpoint: String) {
        context.developerDataStore.edit { preferences ->
            preferences[Keys.API_ENDPOINT] = endpoint
        }
    }

    override suspend fun setMockTelephonyEnabled(enabled: Boolean) {
        context.developerDataStore.edit { preferences ->
            preferences[Keys.MOCK_TELEPHONY_ENABLED] = enabled
        }
    }

    override suspend fun setGatewayServerEnabled(enabled: Boolean) {
        context.developerDataStore.edit { preferences ->
            preferences[Keys.GATEWAY_SERVER_ENABLED] = enabled
        }
    }

    override suspend fun setGatewayPort(port: Int) {
        context.developerDataStore.edit { preferences ->
            preferences[Keys.GATEWAY_PORT] = port
        }
    }

    override suspend fun addApiKey(keyRecord: ApiKeyRecord) {
        context.developerDataStore.edit { preferences ->
            val currentRaw = preferences[Keys.API_KEYS_JSON]
            val currentList = if (currentRaw.isNullOrBlank()) {
                listOf(defaultMasterKey)
            } else {
                try {
                    json.decodeFromString<List<ApiKeyRecord>>(currentRaw)
                } catch (_: Exception) {
                    listOf(defaultMasterKey)
                }
            }
            val updated = currentList.filterNot { it.id == keyRecord.id } + keyRecord
            preferences[Keys.API_KEYS_JSON] = json.encodeToString(updated)
        }
    }

    override suspend fun revokeApiKey(keyId: String) {
        context.developerDataStore.edit { preferences ->
            val currentRaw = preferences[Keys.API_KEYS_JSON]
            if (!currentRaw.isNullOrBlank()) {
                try {
                    val currentList = json.decodeFromString<List<ApiKeyRecord>>(currentRaw)
                    val updated = currentList.filterNot { it.id == keyId }
                    preferences[Keys.API_KEYS_JSON] = json.encodeToString(updated)
                } catch (_: Exception) {
                }
            }
        }
    }

    override suspend fun addWebhook(webhookRecord: WebhookRecord) {
        context.developerDataStore.edit { preferences ->
            val currentRaw = preferences[Keys.WEBHOOKS_JSON]
            val currentList = if (currentRaw.isNullOrBlank()) {
                emptyList()
            } else {
                try {
                    json.decodeFromString<List<WebhookRecord>>(currentRaw)
                } catch (_: Exception) {
                    emptyList()
                }
            }
            val updated = currentList.filterNot { it.id == webhookRecord.id } + webhookRecord
            preferences[Keys.WEBHOOKS_JSON] = json.encodeToString(updated)
        }
    }

    override suspend fun deleteWebhook(webhookId: String) {
        context.developerDataStore.edit { preferences ->
            val currentRaw = preferences[Keys.WEBHOOKS_JSON]
            if (!currentRaw.isNullOrBlank()) {
                try {
                    val currentList = json.decodeFromString<List<WebhookRecord>>(currentRaw)
                    val updated = currentList.filterNot { it.id == webhookId }
                    preferences[Keys.WEBHOOKS_JSON] = json.encodeToString(updated)
                } catch (_: Exception) {
                }
            }
        }
    }

    override suspend fun clearAll() {
        context.developerDataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
