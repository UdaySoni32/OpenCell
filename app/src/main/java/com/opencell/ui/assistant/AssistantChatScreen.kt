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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
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
import com.opencell.ai.Role
import com.opencell.assistant.AssistantIntent
import com.opencell.assistant.AssistantReminder
import com.opencell.assistant.AssistantTask
import com.opencell.assistant.AssistantTaskCommandParser
import com.opencell.assistant.NO_PROVIDER_MESSAGE
import com.opencell.assistant.AssistantMessage
import com.opencell.assistant.AssistantThread
import com.opencell.assistant.ReminderScheduler
import com.opencell.assistant.assistantErrorMessage
import com.opencell.policy.ActionRequest
import com.opencell.policy.ActionResult
import com.opencell.policy.ActionType
import com.opencell.policy.Actor
import com.opencell.memory.MemoryFact
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

data class PendingAssistantAction(val to: String, val body: String, val reason: String?)

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantChatViewModel(private val container: AppContainer) : ViewModel() {
    private val dao = container.database.assistantDao()
    private val memory = container.database.memoryDao()
    private val tasks = container.database.assistantTaskDao()
    private val orchestrator = container.assistantOrchestrator
    private val threadId = MutableStateFlow<Long?>(null)
    private val threadMutex = Mutex()
    private val _sending = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _pendingAction = MutableStateFlow<PendingAssistantAction?>(null)

    val messages: StateFlow<List<AssistantMessage>> = threadId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else dao.observeMessages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sending: StateFlow<Boolean> = _sending.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()
    val pendingAction: StateFlow<PendingAssistantAction?> = _pendingAction.asStateFlow()

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

                AssistantTaskCommandParser.reminder(content)?.let { draft ->
                    createReminder(id, draft.title, draft.triggerAtMillis)
                    return@launch
                }
                AssistantTaskCommandParser.task(content)?.let { draft ->
                    val at = System.currentTimeMillis()
                    withContext(Dispatchers.IO) {
                        tasks.insertTask(AssistantTask(title = draft.title, dueAtMillis = draft.dueAtMillis, createdAtMillis = at, updatedAtMillis = at))
                    }
                    storeAssistantMessage(id, "Created a task: ${draft.title}")
                    return@launch
                }
                AssistantTaskCommandParser.completeTask(content)?.let { title ->
                    val task = withContext(Dispatchers.IO) { tasks.openTasks().firstOrNull { it.title.equals(title, ignoreCase = true) || it.title.contains(title, ignoreCase = true) } }
                    if (task == null) storeAssistantMessage(id, "I could not find an open task matching \"$title\".")
                    else {
                        withContext(Dispatchers.IO) { tasks.completeTask(task.id) }
                        storeAssistantMessage(id, "Completed the task: ${task.title}")
                    }
                    return@launch
                }

                val history = withContext(Dispatchers.IO) { dao.messages(id) }
                    .map { ChatMessage(it.role, it.content) }
                val decision = orchestrator.decide(content, history).getOrElse { error ->
                    _error.value = if (error.message == NO_PROVIDER_MESSAGE) NO_PROVIDER_MESSAGE else assistantErrorMessage(error)
                    storeAssistantMessage(id, _error.value ?: "The assistant could not respond.")
                    return@launch
                }
                when (val intent = decision.intent) {
                    is AssistantIntent.Answer -> storeAssistantMessage(id, intent.message)
                    is AssistantIntent.AskClarification -> storeAssistantMessage(id, intent.message)
                    is AssistantIntent.SendSms -> {
                        _pendingAction.value = PendingAssistantAction(intent.to, intent.body, intent.reason)
                        storeAssistantMessage(id, "I drafted an SMS to ${intent.to}:\n\n\"${intent.body}\"\n\nReview it below before sending.")
                    }
                    is AssistantIntent.Remember -> {
                        val at = System.currentTimeMillis()
                        withContext(Dispatchers.IO) {
                            memory.insert(
                                MemoryFact(
                                    contactKey = null,
                                    text = intent.text,
                                    source = "Assistant suggestion",
                                    createdAtMillis = at,
                                    category = intent.category,
                                    sourceType = MemorySourceType.INFERRED,
                                    confidence = 0.65,
                                    needsConfirmation = true,
                                    updatedAtMillis = at,
                                ),
                            )
                        }
                        storeAssistantMessage(id, "I found a possible memory:\n\n${intent.text}\n\nReview it in What it remembers before I use it.")
                    }
                    is AssistantIntent.Forget -> storeAssistantMessage(id, "I found a request to forget \"${intent.text}\". Open What it remembers to review and delete that memory.")
                    is AssistantIntent.CreateTask -> {
                        val at = System.currentTimeMillis()
                        withContext(Dispatchers.IO) {
                            tasks.insertTask(AssistantTask(title = intent.title, details = intent.details, dueAtMillis = intent.dueAtMillis, createdAtMillis = at, updatedAtMillis = at))
                        }
                        storeAssistantMessage(id, "Created a task: ${intent.title}")
                    }
                    is AssistantIntent.CompleteTask -> {
                        val task = withContext(Dispatchers.IO) { tasks.openTasks().firstOrNull { it.title.contains(intent.title, ignoreCase = true) } }
                        if (task == null) storeAssistantMessage(id, "I could not find an open task matching \"${intent.title}\".")
                        else {
                            withContext(Dispatchers.IO) { tasks.completeTask(task.id) }
                            storeAssistantMessage(id, "Completed the task: ${task.title}")
                        }
                    }
                    is AssistantIntent.CreateReminder -> createReminder(id, intent.title, intent.triggerAtMillis)
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

    fun approvePendingAction() {
        val pending = _pendingAction.value ?: return
        viewModelScope.launch {
            val result = container.actionExecutor.submit(
                ActionRequest(Actor.User, ActionType.SEND_SMS, pending.to, pending.body),
            )
            val message = when (result) {
                is ActionResult.Done -> "SMS queued for ${pending.to}."
                is ActionResult.Pending -> "The SMS is waiting for approval on the phone."
                is ActionResult.Denied -> "The SMS was not sent: ${result.reason}."
                is ActionResult.Failed -> "The SMS could not be sent: ${result.reason}."
            }
            storeAssistantMessage(ensureThread(), message)
            _pendingAction.value = null
        }
    }

    fun cancelPendingAction() {
        if (_pendingAction.value == null) return
        _pendingAction.value = null
        viewModelScope.launch { storeAssistantMessage(ensureThread(), "I cancelled that SMS draft.") }
    }

    private suspend fun createReminder(thread: Long, title: String, triggerAtMillis: Long) {
        val now = System.currentTimeMillis()
        if (triggerAtMillis <= now) {
            storeAssistantMessage(thread, "That reminder time has already passed. Tell me a future time.")
            return
        }
        val reminder = AssistantReminder(title = title, triggerAtMillis = triggerAtMillis, createdAtMillis = now)
        val id = withContext(Dispatchers.IO) { tasks.insertReminder(reminder) }
        ReminderScheduler.schedule(container.appContext, reminder.copy(id = id))
        storeAssistantMessage(thread, "Reminder set for ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(triggerAtMillis))}: $title")
    }

    private suspend fun storeAssistantMessage(id: Long, text: String) {
        val at = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            dao.insertMessage(AssistantMessage(threadId = id, role = Role.ASSISTANT, content = text, createdAtMillis = at))
            dao.touchThread(id, at)
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
    val pending by vm.pendingAction.collectAsState()
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
                            "Ask your assistant anything. External actions are previewed before they run.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(messages, key = { it.id }) { message -> AssistantBubble(message) }
        }
        pending?.let { action ->
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Ready to send this SMS?", style = MaterialTheme.typography.titleMedium)
                    Text("To ${action.to}: ${action.body}", modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = vm::cancelPendingAction) { Text("Cancel") }
                        FilledTonalButton(onClick = vm::approvePendingAction, modifier = Modifier.padding(start = 8.dp)) { Text("Send") }
                    }
                }
            }
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
