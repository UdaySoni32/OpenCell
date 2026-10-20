package com.opencell.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencell.AppContainer
import com.opencell.memory.MemoryFact
import com.opencell.memory.MemorySourceType
import com.opencell.ui.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MemoryViewModel(private val container: AppContainer) : ViewModel() {
    private val dao = container.database.memoryDao()
    val facts = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun delete(f: MemoryFact) = viewModelScope.launch { dao.delete(f) }
    fun confirm(f: MemoryFact) = viewModelScope.launch { dao.confirm(f.id) }
    fun updateText(f: MemoryFact, text: String) = viewModelScope.launch { dao.updateText(f.id, text.trim().take(300)) }
    fun add(text: String) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        dao.insert(
            MemoryFact(
                contactKey = null,
                text = text.trim().take(300),
                source = "added by you",
                createdAtMillis = now,
                sourceType = MemorySourceType.USER_CONFIRMED,
                updatedAtMillis = now,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val vm = appViewModel { MemoryViewModel(it) }
    val facts by vm.facts.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<MemoryFact?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("What it remembers") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        Row(Modifier.padding(16.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(draft, { draft = it }, label = { Text("Add a fact about you") }, singleLine = true, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = { vm.add(draft); draft = "" }, enabled = draft.isNotBlank(), modifier = Modifier.padding(start = 8.dp)) { Text("Add") }
        }
        if (facts.isEmpty()) {
            Text("Nothing yet. Turn on \"Learn from my SMS\" or add facts yourself.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
        LazyColumn {
            items(facts, key = { it.id }) { f ->
                ListItem(
                    headlineContent = { Text(f.text) },
                    supportingContent = {
                        Column {
                            Text(if (f.contactKey == null) "About you · ${f.source}" else "About …${f.contactKey.takeLast(4)} · ${f.source}")
                            if (f.needsConfirmation) Text("Review before the assistant uses this", color = MaterialTheme.colorScheme.secondary)
                        }
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (f.needsConfirmation) FilledTonalButton(onClick = { vm.confirm(f) }) { Text("Keep") }
                            IconButton(onClick = { editing = f }) { Icon(Icons.Filled.Edit, "Edit") }
                            IconButton(onClick = { vm.delete(f) }) { Icon(Icons.Filled.Delete, "Forget") }
                        }
                    },
                )
            }
        }
    }
    editing?.let { fact ->
        MemoryEditDialog(
            fact = fact,
            onDismiss = { editing = null },
            onSave = { text -> vm.updateText(fact, text); editing = null },
        )
    }
}

@Composable
private fun MemoryEditDialog(fact: MemoryFact, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable(fact.id) { mutableStateOf(fact.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit memory") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = false, minLines = 2) },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
