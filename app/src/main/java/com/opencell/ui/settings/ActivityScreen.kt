package com.opencell.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencell.AppContainer
import com.opencell.policy.ApprovalEntity
import com.opencell.policy.AuditEntry
import com.opencell.policy.Outcome
import com.opencell.policy.PolicyMode
import com.opencell.policy.PolicySettings
import com.opencell.ui.appViewModel
import com.opencell.ui.relativeTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ActivityViewModel(private val container: AppContainer) : ViewModel() {
    val policy = container.settings.policy.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PolicySettings())
    val pending = container.database.policyDao().observePending().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val audit = container.database.policyDao().observeAudit().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun update(transform: (PolicySettings) -> PolicySettings) = viewModelScope.launch { container.settings.updatePolicy(transform) }
    fun decide(approval: ApprovalEntity, approve: Boolean) = viewModelScope.launch { container.actionExecutor.decide(approval.id, approve, "you") }
}

private val modeText = mapOf(
    PolicyMode.ASK to ("Ask me every time" to "You approve each call or SMS"),
    PolicyMode.ALLOWLIST to ("Auto for allowed numbers" to "Ask for everyone else"),
    PolicyMode.FULL_AUTO to ("Full auto" to "Act without asking, up to the daily limit"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(onBack: () -> Unit) {
    val vm = appViewModel { ActivityViewModel(it) }
    val policy by vm.policy.collectAsState()
    val pending by vm.pending.collectAsState()
    val audit by vm.audit.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Permissions & activity") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                ListItem(
                    headlineContent = { Text("Kill switch") },
                    supportingContent = { Text("Block every AI and computer action") },
                    trailingContent = { Switch(checked = policy.killSwitch, onCheckedChange = { on -> vm.update { it.copy(killSwitch = on) } }) },
                )
                Text("When automation wants to call or text", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 8.dp))
            }
            items(PolicyMode.entries) { mode ->
                val (title, sub) = modeText.getValue(mode)
                ListItem(
                    leadingContent = { RadioButton(selected = policy.mode == mode, onClick = null) },
                    headlineContent = { Text(title) },
                    supportingContent = { Text(sub) },
                    modifier = Modifier.selectable(selected = policy.mode == mode, onClick = { vm.update { it.copy(mode = mode) } }),
                )
            }
            item { PolicyFields(policy, vm) }
            if (pending.isNotEmpty()) {
                item { Text("Waiting for you", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
                items(pending, key = { "p${it.id}" }) { PendingCard(it, vm) }
            }
            item { Text("Activity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
            items(audit, key = { "a${it.id}" }) { AuditRow(it) }
        }
    }
}

@Composable
private fun PolicyFields(policy: PolicySettings, vm: ActivityViewModel) {
    var allowlist by rememberSaveable(policy.allowlist) { mutableStateOf(policy.allowlist.joinToString(", ")) }
    var limit by rememberSaveable(policy.dailyLimit) { mutableStateOf(policy.dailyLimit.toString()) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        if (policy.mode == PolicyMode.ALLOWLIST) {
            OutlinedTextField(
                value = allowlist,
                onValueChange = { allowlist = it },
                label = { Text("Allowed numbers (comma separated)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = limit,
            onValueChange = { limit = it.filter(Char::isDigit).take(4) },
            label = { Text("Daily limit (automated calls + SMS)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        FilledTonalButton(
            onClick = {
                val numbers = allowlist.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                vm.update { it.copy(allowlist = numbers, dailyLimit = limit.toIntOrNull() ?: it.dailyLimit) }
            },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Save") }
    }
}

@Composable
private fun PendingCard(a: ApprovalEntity, vm: ActivityViewModel) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("${a.actorLabel} → ${a.action.label} ${a.target}", style = MaterialTheme.typography.titleMedium)
            a.body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(relativeTime(a.createdAtMillis), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { vm.decide(a, false) }) { Text("Deny") }
                FilledTonalButton(onClick = { vm.decide(a, true) }, modifier = Modifier.padding(start = 8.dp)) { Text("Approve") }
            }
        }
    }
}

@Composable
private fun AuditRow(e: AuditEntry) {
    val color = when (e.outcome) {
        Outcome.DONE -> MaterialTheme.colorScheme.primary
        Outcome.PENDING -> MaterialTheme.colorScheme.secondary
        Outcome.DENIED, Outcome.FAILED -> MaterialTheme.colorScheme.error
    }
    ListItem(
        headlineContent = { Text("${e.actor}: ${e.action.label} ${e.target}") },
        supportingContent = { Text(listOfNotNull(e.body, e.detail).joinToString(" · ").ifEmpty { " " }) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(e.outcome.name.lowercase(), color = color, style = MaterialTheme.typography.labelSmall)
                Text(relativeTime(e.atMillis), style = MaterialTheme.typography.labelSmall)
            }
        },
    )
}
