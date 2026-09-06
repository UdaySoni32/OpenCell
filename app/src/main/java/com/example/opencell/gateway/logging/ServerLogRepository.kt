package com.example.opencell.gateway.logging

import com.example.opencell.domain.model.ServerLogEntry
import com.example.opencell.domain.model.ServerLogType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ServerLogRepository {

    private val maxLogs = 200
    private val _logs = MutableStateFlow<List<ServerLogEntry>>(emptyList())
    val logs: StateFlow<List<ServerLogEntry>> = _logs.asStateFlow()

    fun log(type: ServerLogType, message: String, details: String? = null) {
        val entry = ServerLogEntry(type = type, message = message, details = details)
        val updated = (listOf(entry) + _logs.value).take(maxLogs)
        _logs.value = updated
    }

    fun clear() {
        _logs.value = emptyList()
    }

    companion object {
        val instance by lazy { ServerLogRepository() }
    }
}
