package com.opencell.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencell.AppContainer
import com.opencell.assistant.AssistantTask
import com.opencell.ui.appViewModel
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TasksViewModel(private val container: AppContainer) : ViewModel() {
    private val dao = container.database.assistantTaskDao()
    val tasks = dao.observeOpenTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun complete(task: AssistantTask) = viewModelScope.launch { dao.completeTask(task.id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(onBack: () -> Unit) {
    val vm = appViewModel { TasksViewModel(it) }
    val tasks by vm.tasks.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Tasks & reminders") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        if (tasks.isEmpty()) {
            Text("No open tasks. Ask the assistant to create one.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn {
                items(tasks, key = { it.id }) { task ->
                    ListItem(
                        headlineContent = { Text(task.title) },
                        supportingContent = {
                            task.dueAtMillis?.let { Text("Due ${DateFormat.getDateTimeInstance().format(Date(it))}") }
                        },
                        trailingContent = { IconButton(onClick = { vm.complete(task) }) { Icon(Icons.Filled.Check, "Complete") } },
                    )
                }
            }
        }
    }
}
