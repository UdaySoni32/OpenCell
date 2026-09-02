package io.opencell.ui.home

import android.content.Context
import android.provider.CallLog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.opencell.core.database.entity.CallEntity
import io.opencell.platform.contacts.Contact
import io.opencell.platform.contacts.ContactEngine
import io.opencell.platform.devices.DeviceEngine
import io.opencell.platform.telecom.CallEngine
import io.opencell.ui.dialer.CallDisplayInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ContactDisplayInfo(
    val contact: Contact
)

data class HomeUiState(
    val contacts: List<ContactDisplayInfo> = emptyList(),
    val recentCalls: List<CallDisplayInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastRefreshTime: Long = 0L
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callEngine: CallEngine,
    private val deviceEngine: DeviceEngine,
    private val contactEngine: ContactEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadContacts()
        observeRecentCalls()
        importSystemCallLog()
    }

    /**
     * Called on resume to refresh contacts and call history.
     */
    fun onResume() {
        loadContacts()
        importSystemCallLog()
    }

    fun refresh() {
        loadContacts()
        importSystemCallLog()
        _uiState.value = _uiState.value.copy(lastRefreshTime = System.currentTimeMillis())
    }

    private fun loadContacts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val contacts = withContext(Dispatchers.IO) {
                val starred = contactEngine.getStarredContacts(limit = 20)
                if (starred.isNotEmpty()) starred
                else contactEngine.getAllContacts(limit = 20)
            }
            _uiState.value = _uiState.value.copy(
                contacts = contacts.map { ContactDisplayInfo(it) },
                isLoading = false
            )
        }
    }

    /**
     * Import recent calls from the system call log (default phone app history).
     */
    private fun importSystemCallLog() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val projection = arrayOf(
                        CallLog.Calls._ID,
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.CACHED_NAME,
                        CallLog.Calls.TYPE,
                        CallLog.Calls.DATE,
                        CallLog.Calls.DURATION
                    )
                    val cursor = context.contentResolver.query(
                        CallLog.Calls.CONTENT_URI,
                        projection,
                        null, null,
                        "${CallLog.Calls.DATE} DESC"
                    )
                    cursor?.use { c ->
                        val idIdx = c.getColumnIndex(CallLog.Calls._ID)
                        val numberIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                        val nameIdx = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                        val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)
                        val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)
                        val durationIdx = c.getColumnIndex(CallLog.Calls.DURATION)

                        val systemCalls = mutableListOf<CallDisplayInfo>()
                        var count = 0
                        while (c.moveToNext() && count < 50) {
                            val id = c.getString(idIdx) ?: continue
                            val number = c.getString(numberIdx) ?: ""
                            val cachedName = c.getString(nameIdx)
                            val type = c.getInt(typeIdx)
                            val date = c.getLong(dateIdx)
                            val duration = c.getLong(durationIdx) * 1000

                            val direction = when (type) {
                                CallLog.Calls.INCOMING_TYPE -> "INBOUND"
                                CallLog.Calls.OUTGOING_TYPE -> "OUTBOUND"
                                CallLog.Calls.MISSED_TYPE -> "INBOUND"
                                else -> "INBOUND"
                            }
                            val state = when (type) {
                                CallLog.Calls.MISSED_TYPE -> "MISSED"
                                else -> "ENDED"
                            }

                            // Use system cached name, or look up from contacts
                            val resolvedName = cachedName ?: if (number.isNotBlank()) {
                                contactEngine.lookupByPhoneNumber(number)?.displayName
                            } else null

                            val entity = CallEntity(
                                id = "sys_$id",
                                deviceId = "system_import",
                                subscriptionId = 0,
                                direction = direction,
                                fromNumber = if (direction == "INBOUND") number else "",
                                toNumber = if (direction == "OUTBOUND") number else "",
                                state = state,
                                startedAt = date,
                                durationMs = duration
                            )
                            systemCalls.add(CallDisplayInfo(call = entity, contactName = resolvedName))
                            count++
                        }

                        val existingNumbers = _uiState.value.recentCalls.map {
                            val num = if (it.call.direction == "INBOUND") it.call.fromNumber else it.call.toNumber
                            "${num}_${it.call.startedAt / 60000}"
                        }.toSet()

                        val newCalls = systemCalls.filter { sys ->
                            val num = if (sys.call.direction == "INBOUND") sys.call.fromNumber else sys.call.toNumber
                            "${num}_${sys.call.startedAt / 60000}" !in existingNumbers
                        }

                        if (newCalls.isNotEmpty()) {
                            val merged = (newCalls + _uiState.value.recentCalls)
                                .sortedByDescending { it.call.startedAt }
                                .take(50)
                            _uiState.value = _uiState.value.copy(recentCalls = merged)
                        }
                    }
                } catch (e: Exception) {
                    // Permission not granted or query failed
                }
            }
        }
    }

    private fun observeRecentCalls() {
        viewModelScope.launch {
            callEngine.getCallHistory()
                .catch { /* ignore DB errors */ }
                .collect { calls ->
                    val displayCalls = withContext(Dispatchers.IO) {
                        calls.take(50).map { call ->
                            val number = if (call.direction == "INBOUND") call.fromNumber else call.toNumber
                            val name = if (number.isNotBlank()) {
                                contactEngine.lookupByPhoneNumber(number)?.displayName
                            } else null
                            CallDisplayInfo(call = call, contactName = name)
                        }
                    }
                    _uiState.value = _uiState.value.copy(recentCalls = displayCalls)
                }
        }
    }
}
