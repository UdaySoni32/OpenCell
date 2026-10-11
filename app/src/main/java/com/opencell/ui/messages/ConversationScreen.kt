package com.opencell.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencell.AppContainer
import com.opencell.sms.SmsMessage
import com.opencell.sms.SmsStatus
import com.opencell.ui.appViewModel
import com.opencell.ui.relativeTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModel(private val container: AppContainer, threadId: Long?, initialAddress: String) : ViewModel() {
    val address = MutableStateFlow(initialAddress)
    private val thread = MutableStateFlow(threadId)

    val messages: StateFlow<List<SmsMessage>> = thread
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else container.smsRepository.observeMessages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        threadId?.let { viewModelScope.launch { container.smsRepository.markThreadRead(it) } }
    }

    fun send(body: String) {
        val to = address.value
        if (to.isBlank() || body.isBlank()) return
        viewModelScope.launch {
            container.smsRepository.send(to, body)
            if (thread.value == null) thread.value = container.smsRepository.threadIdFor(to)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(threadId: Long?, address: String, onBack: () -> Unit, onCall: (String) -> Unit) {
    val vm = appViewModel(key = "conv-$threadId-$address") { ConversationViewModel(it, threadId, address) }
    val messages by vm.messages.collectAsState()
    val to by vm.address.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = {
                if (threadId == null && address.isEmpty()) {
                    OutlinedTextField(value = to, onValueChange = { vm.address.value = it }, placeholder = { Text("To: number") }, singleLine = true)
                } else {
                    Text(to)
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { if (to.isNotBlank()) IconButton(onClick = { onCall(to) }) { Icon(Icons.Filled.Call, "Call") } },
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(messages, key = { it.id }) { Bubble(it) }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
                maxLines = 5,
            )
            IconButton(onClick = { vm.send(draft); draft = "" }, enabled = draft.isNotBlank() && to.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send")
            }
        }
    }
}

@Composable
private fun Bubble(m: SmsMessage) {
    val mine = !m.isIncoming
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(18.dp, 18.dp, if (mine) 4.dp else 18.dp, if (mine) 18.dp else 4.dp),
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(m.body, color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            Text(
                relativeTime(m.timestampMillis) + if (mine) " · ${statusLabel(m.status)}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = (if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.7f),
            )
        }
    }
}

private fun statusLabel(s: SmsStatus) = when (s) {
    SmsStatus.PENDING -> "sending"
    SmsStatus.SENT -> "sent"
    SmsStatus.DELIVERED -> "delivered"
    SmsStatus.FAILED -> "failed"
    SmsStatus.RECEIVED -> ""
}
