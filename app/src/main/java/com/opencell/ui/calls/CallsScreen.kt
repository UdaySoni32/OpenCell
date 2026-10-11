package com.opencell.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencell.AppContainer
import com.opencell.calllog.CallEntry
import com.opencell.calllog.CallKind
import com.opencell.calllog.CallSource
import com.opencell.ui.appViewModel
import com.opencell.ui.formatDuration
import com.opencell.ui.initialOf
import com.opencell.ui.relativeTime
import com.opencell.ui.theme.accent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class CallFilter(val label: String) { ALL("All"), MISSED("Missed"), PHONE("Phone"), APPS("Apps") }

data class CallsUiState(val filter: CallFilter = CallFilter.ALL, val calls: List<CallEntry> = emptyList())

fun applyFilter(calls: List<CallEntry>, filter: CallFilter): List<CallEntry> = when (filter) {
    CallFilter.ALL -> calls
    CallFilter.MISSED -> calls.filter { it.kind == CallKind.MISSED }
    CallFilter.PHONE -> calls.filter { it.source == CallSource.SIM }
    CallFilter.APPS -> calls.filter { it.source != CallSource.SIM }
}

class CallsViewModel(private val container: AppContainer) : ViewModel() {
    private val filter = MutableStateFlow(CallFilter.ALL)

    val state: StateFlow<CallsUiState> = combine(container.unifiedCalls(), filter) { calls, f -> CallsUiState(f, applyFilter(calls, f)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CallsUiState())

    fun setFilter(f: CallFilter) {
        filter.value = f
    }

    fun callBack(entry: CallEntry): Boolean = container.callbackLauncher.callBack(entry)
}

@Composable
fun CallsScreen(onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    val vm = appViewModel { CallsViewModel(it) }
    val state by vm.state.collectAsState()
    Column(modifier.fillMaxSize()) {
        Text("Calls", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CallFilter.entries.forEach { f ->
                FilterChip(selected = state.filter == f, onClick = { vm.setFilter(f) }, label = { Text(f.label) })
            }
        }
        if (state.calls.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No calls yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.calls, key = { it.id }) { entry ->
                    CallRow(entry) {
                        if (!vm.callBack(entry)) onMessage("Can't call back via ${entry.source.label}")
                    }
                }
            }
        }
    }
}

@Composable
private fun CallRow(entry: CallEntry, onClick: () -> Unit) {
    val missed = entry.kind == CallKind.MISSED
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(entry.source.accent().copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(initialOf(entry.displayName), color = entry.source.accent(), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(kindIcon(entry.kind), null, Modifier.size(14.dp), tint = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(
                    buildString {
                        append(relativeTime(entry.timestampMillis))
                        if (entry.durationSeconds > 0) append(" · ").append(formatDuration(entry.durationSeconds))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SourceTag(entry.source)
    }
}

@Composable
private fun SourceTag(source: CallSource) {
    Text(
        source.label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(source.accent()).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun kindIcon(kind: CallKind) = when (kind) {
    CallKind.INCOMING, CallKind.RINGING -> Icons.AutoMirrored.Filled.CallReceived
    CallKind.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
    CallKind.MISSED -> Icons.AutoMirrored.Filled.CallMissed
    CallKind.REJECTED -> Icons.Filled.CallEnd
}
