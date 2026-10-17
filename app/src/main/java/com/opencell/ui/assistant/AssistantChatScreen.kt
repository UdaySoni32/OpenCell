package com.opencell.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.opencell.ai.ChatMessage
import com.opencell.ai.LlmException
import com.opencell.ai.Role
import com.opencell.assistant.NO_PROVIDER_MESSAGE
import com.opencell.assistant.AssistantMessage
import com.opencell.assistant.AssistantPrompt
import com.opencell.assistant.AssistantThread
import com.opencell.assistant.assistantErrorMessage
import com.opencell.assistant.buildAssistantPrompt
import com.opencell.memory.MemoryFact
import com.opencell.memory.MemoryRetriever
import com.opencell.memory.MemorySourceType
import com.opencell.memory.RememberCommandParser
import com.opencell.ui.appViewModel
import com.opencell.ui.relativeTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantChatViewModel(private val container: AppContainer) : ViewModel() {
    private val dao = container.database.assistantDao()
    private val memory = container.database.memoryDao()
    private val threadId = MutableStateFlow<Long?>(null)
    private val threadMutex = Mutex()
    private val _sending = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val messages: StateFlow<List<AssistantMessage>> = threadId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeMessages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sending: StateFlow<Boolean> = _sending.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch { ensureThread() }
    }

    fun clearError() {
        _error.value = null
    }

    fun send(text: String) {
        val content = text.trim()
        if (content.isBlank() || _sending.value) return
        viewModelScope.launch {
            _sending.value = true
            _error.value = null
            try {
                val id = ensureThread()
                val now = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    dao.insertMessage(AssistantMessage(threadId = id, role = Role.USER, content = content, createdAtMillis = now))
                    dao.touchThread(id, now)
                }

                RememberCommandParser.parse(content)?.let { command ->
                    withContext(Dispatchers.IO) {
                        memory.insert(
                            MemoryFact(
                                contactKey = null,
                                text = command.text,
                                source = "Assistant chat",
                                createdAtMillis = now,
                                category = command.category,
                                sourceType = MemorySourceType.USER_CONFIRMED,
                                confidence = 1.0,
                                updatedAtMillis = now,
                            ),
                        )
                        dao.insertMessage(
                            AssistantMessage(
                                threadId = id,
                                role = Role.ASSISTANT,
                                content = "I’ll remember that: ${command.text}",
                                createdAtMillis = now,
                            ),
                        )
                        dao.touchThread(id, now)
                    }
                    return@launch
                }

                val settings = container.aiSettings.current()
                val instructions = container.aiSettings.currentInstructions()
                val llm = container.aiSettings.buildLlm()
                if (llm == null) {
                    _error.value = NO_PROVIDER_MESSAGE
                    return@launch
                }

                val history = withContext(Dispatchers.IO) { dao.messages(id) }
                    .map { ChatMessage(it.role, it.content) }
                val memories = withContext(Dispatchers.IO) {
                    val candidates = memory.relevant(null, limit = 80)
                    MemoryRetriever.retrieve(candidates, contactKey = null, query = content, limit = 20)
                }
                val prompt: AssistantPrompt = buildAssistantPrompt(settings, instructions, history, memories)
                val answer = withContext(Dispatchers.IO) {
                    llm.complete(prompt.system, prompt.messages).trim()
                }
                if (answer.isBlank()) throw LlmException("AI returned an empty response")

                val responseAt = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    dao.insertMessage(AssistantMessage(threadId = id, role = Role.ASSISTANT, content = answer, createdAtMillis = responseAt))
                    dao.touchThread(id, responseAt)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = assistantErrorMessage(e)
            } finally {
                _sending.value = false
            }
        }
    }

    private suspend fun ensureThread(): Long {
        threadId.value?.let { return it }
        return threadMutex.withLock {
            threadId.value?.let { return@withLock it }
            val id = withContext(Dispatchers.IO) {
                dao.latestThread()?.id ?: run {
                    val now = System.currentTimeMillis()
                    dao.insertThread(AssistantThread(title = "Assistant", createdAtMillis = now, updatedAtMillis = now))
                }
            }
            threadId.value = id
            id
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantChatScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val vm = appViewModel { AssistantChatViewModel(it) }
    val messages by vm.messages.collectAsState()
    val sending by vm.sending.collectAsState()
    val error by vm.error.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text("Assistant") },
            actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Assistant settings") } },
        )
        error?.let { text ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                onClick = vm::clearError,
            ) {
                Text(text, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Ask your assistant anything. This chat does not send SMS or place calls.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(messages, key = { it.id }) { message -> AssistantBubble(message) }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message assistant") },
                modifier = Modifier.weight(1f),
                maxLines = 5,
                enabled = !sending,
            )
            IconButton(
                onClick = { vm.send(draft); draft = "" },
                enabled = draft.isNotBlank() && !sending,
            ) {
                if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Filled.Send, "Send")
            }
        }
    }
}

@Composable
private fun AssistantBubble(message: AssistantMessage) {
    val mine = message.role == Role.USER
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .background(
                    if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(18.dp, 18.dp, if (mine) 4.dp else 18.dp, if (mine) 18.dp else 4.dp),
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(message.content, color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            Text(
                relativeTime(message.createdAtMillis),
                style = MaterialTheme.typography.labelSmall,
                color = (if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.7f),
            )
        }
    }
}
