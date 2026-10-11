package com.opencell.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.opencell.policy.PolicyMode
import com.opencell.policy.PolicySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Non-secret user settings. Secrets (AI keys) never go here. */
class SettingsStore(private val context: Context) {

    val policy: Flow<PolicySettings> = context.dataStore.data.map { it.toPolicy() }
    val gatewayEnabled: Flow<Boolean> = context.dataStore.data.map { it[GATEWAY_ENABLED] ?: false }

    suspend fun currentPolicy(): PolicySettings = policy.first()
    suspend fun isGatewayEnabled(): Boolean = gatewayEnabled.first()

    suspend fun updatePolicy(transform: (PolicySettings) -> PolicySettings) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs.toPolicy())
            prefs[MODE] = next.mode.name
            prefs[ALLOWLIST] = next.allowlist
            prefs[DAILY_LIMIT] = next.dailyLimit
            prefs[KILL_SWITCH] = next.killSwitch
        }
    }

    suspend fun isGuideDone(): Boolean = context.dataStore.data.first()[GUIDE_DONE] ?: false

    suspend fun setGuideDone() {
        context.dataStore.edit { it[GUIDE_DONE] = true }
    }

    suspend fun setGatewayEnabled(enabled: Boolean) {
        context.dataStore.edit { it[GATEWAY_ENABLED] = enabled }
    }

    private fun Preferences.toPolicy() = PolicySettings(
        mode = this[MODE]?.let { runCatching { PolicyMode.valueOf(it) }.getOrNull() } ?: PolicyMode.ASK,
        allowlist = this[ALLOWLIST] ?: emptySet(),
        dailyLimit = this[DAILY_LIMIT] ?: PolicySettings().dailyLimit,
        killSwitch = this[KILL_SWITCH] ?: false,
    )

    private companion object {
        val MODE = stringPreferencesKey("policy_mode")
        val ALLOWLIST = stringSetPreferencesKey("policy_allowlist")
        val DAILY_LIMIT = intPreferencesKey("policy_daily_limit")
        val KILL_SWITCH = booleanPreferencesKey("policy_kill_switch")
        val GATEWAY_ENABLED = booleanPreferencesKey("gateway_enabled")
        val GUIDE_DONE = booleanPreferencesKey("guide_done")
    }
}
