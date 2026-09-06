package com.example.opencell.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.opencell.data.local.preferences.SettingsDataStore
import com.example.opencell.data.local.preferences.ThemeMode
import com.example.opencell.telecom.DefaultDialerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionStatus(
    val permission: String,
    val label: String,
    val isGranted: Boolean
)

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsDataStore.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeMode.SYSTEM
    )

    val dynamicColorEnabled: StateFlow<Boolean> = settingsDataStore.dynamicColorEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    private val _permissionStatuses = MutableStateFlow<List<PermissionStatus>>(emptyList())
    val permissionStatuses: StateFlow<List<PermissionStatus>> = _permissionStatuses.asStateFlow()

    private val _isDefaultDialer = MutableStateFlow(true)
    val isDefaultDialer: StateFlow<Boolean> = _isDefaultDialer.asStateFlow()

    fun refreshPermissions(context: Context) {
        val permissionsList = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION to "Location (GPS / Network)",
            Manifest.permission.READ_PHONE_STATE to "Phone State / Telephony",
            Manifest.permission.CALL_PHONE to "Phone Calls",
            Manifest.permission.CAMERA to "Camera / Field AR View"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsList.add(Manifest.permission.POST_NOTIFICATIONS to "Notifications")
        }

        _permissionStatuses.value = permissionsList.map { (perm, label) ->
            val granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            PermissionStatus(perm, label, granted)
        }

        _isDefaultDialer.value = DefaultDialerManager.isDefaultDialer(context)
    }

    fun getSetDefaultDialerIntent(context: Context) = DefaultDialerManager.createRequestDefaultDialerIntent(context)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsDataStore.setThemeMode(mode)
        }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setDynamicColorEnabled(enabled)
        }
    }

    class Factory(private val dataStore: SettingsDataStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(dataStore) as T
        }
    }
}
