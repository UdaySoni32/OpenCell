package com.opencell.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencell.AppContainer
import com.opencell.ai.AiSettings
import com.opencell.ai.ProviderConfig
import com.opencell.ai.ProviderKind
import com.opencell.ai.ProviderPreset
import com.opencell.ui.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssistantViewModel(private val container: AppContainer) : ViewModel() {
    private val store = container.aiSettings
    val settings = store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiSettings())
    val instructions = store.instructions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val testResult = MutableStateFlow<String?>(null)

    fun hasKey(slot: String) = store.hasKey(slot)
    fun update(transform: (AiSettings) -> AiSettings) = viewModelScope.launch { store.update(transform) }
    fun saveProvider(slot: String, config: ProviderConfig?, key: String?) = viewModelScope.launch {
        store.update { if (slot == "primary") it.copy(primary = config) else it.copy(fallback = config) }
        if (config == null) store.setKey(slot, null) else if (!key.isNullOrBlank()) store.setKey(slot, key)
    }
    fun saveInstructions(text: String) = viewModelScope.launch { store.setInstructions(text) }
    fun test() = viewModelScope.launch {
        testResult.value = "Testing…"
        testResult.value = container.assistant.testProvider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(onBack: () -> Unit, onOpenMemory: () -> Unit) {
    val vm = appViewModel { AssistantViewModel(it) }
    val s by vm.settings.collectAsState()
    val instructions by vm.instructions.collectAsState()
    val test by vm.testResult.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("AI assistant") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Text(
                "Bring your own AI. Everything the assistant sends still follows Permissions & activity.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ProviderCard("primary", "Main AI", s.primary, vm)
            ProviderCard("fallback", "Fallback (optional)", s.fallback, vm)
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = vm::test, enabled = s.primary != null) { Text("Test AI") }
            }
            test?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp, 4.dp)) }

            Text("What it does", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            Toggle("Reply to SMS", "Drafts replies to incoming texts", s.smsAgent) { on -> vm.update { it.copy(smsAgent = on) } }
            Toggle("Only for contacts", "Ignore texts from unknown numbers", s.smsAgentContactsOnly) { on -> vm.update { it.copy(smsAgentContactsOnly = on) } }
            Toggle("Screen unknown callers", "Decline calls from numbers not in contacts and ask by SMS what it's about", s.screenUnknownCalls) { on ->
                vm.update { it.copy(screenUnknownCalls = on) }
            }
            Toggle("Learn from my SMS", "Remember facts from conversations with contacts", s.learnFromSms) { on -> vm.update { it.copy(learnFromSms = on) } }

            Text("About you", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            var name by rememberSaveable(s.userName) { mutableStateOf(s.userName) }
            var text by rememberSaveable(instructions) { mutableStateOf(instructions) }
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    text,
                    { text = it },
                    label = { Text("Instructions") },
                    placeholder = { Text("e.g. I'm a student. Decline sales calls. Reply in Hinglish to friends.") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilledTonalButton(onClick = { vm.update { it.copy(userName = name) }; vm.saveInstructions(text) }) { Text("Save") }
                    OutlinedButton(onClick = onOpenMemory) { Text("What it remembers") }
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(slot: String, title: String, config: ProviderConfig?, vm: AssistantViewModel) {
    var kind by rememberSaveable(config) { mutableStateOf(config?.kind ?: ProviderKind.OPENAI_COMPATIBLE) }
    var url by rememberSaveable(config) { mutableStateOf(config?.baseUrl.orEmpty()) }
    var model by rememberSaveable(config) { mutableStateOf(config?.model.orEmpty()) }
    var key by rememberSaveable(config) { mutableStateOf("") }
    Card(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                config?.let { "Using ${it.model} · ${if (vm.hasKey(slot)) "key saved" else "no key"}" } ?: "Not set",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProviderPreset.entries.forEach { p ->
                    AssistChip(onClick = { kind = p.config.kind; url = p.config.baseUrl; model = p.config.model }, label = { Text(p.label) })
                }
            }
            OutlinedTextField(url, { url = it }, label = { Text(if (kind == ProviderKind.ANTHROPIC) "API URL (Anthropic)" else "Base URL (OpenAI-compatible)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                key,
                { key = it },
                label = { Text("API key (blank = keep / none)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilledTonalButton(
                    onClick = { vm.saveProvider(slot, ProviderConfig(kind, url.trim(), model.trim()), key); key = "" },
                    enabled = url.startsWith("http") && model.isNotBlank(),
                ) { Text("Save") }
                if (config != null) TextButton(onClick = { vm.saveProvider(slot, null, null) }) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}
