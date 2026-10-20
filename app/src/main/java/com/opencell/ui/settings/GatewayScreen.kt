package com.opencell.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencell.AppContainer
import com.opencell.gateway.GatewayService
import com.opencell.gateway.PairedDeviceEntity
import com.opencell.policy.Scope
import com.opencell.ui.appViewModel
import com.opencell.ui.relativeTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GatewayViewModel(private val container: AppContainer) : ViewModel() {
    val enabled = container.settings.gatewayEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val running = GatewayService.running
    val pairingCode = container.pairingManager.code
    val devices = container.database.pairedDeviceDao().observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(on: Boolean, context: android.content.Context) {
        viewModelScope.launch { container.settings.setGatewayEnabled(on) }
        if (on) GatewayService.start(context) else GatewayService.stop(context)
    }

    fun startPairing() = container.pairingManager.startPairing()
    fun cancelPairing() = container.pairingManager.cancelPairing()

    fun toggleScope(device: PairedDeviceEntity, scope: Scope, on: Boolean) = viewModelScope.launch {
        val scopes = Scope.parse(device.scopes).let { if (on) it + scope else it - scope }
        container.database.pairedDeviceDao().update(device.copy(scopes = Scope.format(scopes)))
    }

    fun revoke(device: PairedDeviceEntity) = viewModelScope.launch {
        container.database.pairedDeviceDao().update(device.copy(revoked = true))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GatewayScreen(onBack: () -> Unit) {
    val vm = appViewModel { GatewayViewModel(it) }
    val context = LocalContext.current
    val enabled by vm.enabled.collectAsState()
    val running by vm.running.collectAsState()
    val code by vm.pairingCode.collectAsState()
    val devices by vm.devices.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Computers & API") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            ListItem(
                headlineContent = { Text("Gateway") },
                supportingContent = { Text(if (running) "Running on port ${GatewayService.PORT}" else "Off") },
                trailingContent = { Switch(checked = enabled, onCheckedChange = { vm.setEnabled(it, context) }) },
            )
            if (running) {
                Text(
                    "Local only: 127.0.0.1:${GatewayService.PORT}" +
                        "\nOn your computer run: adb forward tcp:8787 tcp:8787" +
                        "\nThen: opencell pair 127.0.0.1. Direct Wi-Fi/Tailscale access is not enabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(16.dp))
                PairingCard(code?.code, code?.expiresAtMillis, onStart = { vm.startPairing() }, onCancel = vm::cancelPairing)
            }
            Text("Paired computers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            if (devices.isEmpty()) {
                Text("None yet", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
            devices.forEach { DeviceCard(it, vm) }
        }
    }
}

@Composable
private fun PairingCard(code: String?, expiresAt: Long?, onStart: () -> Unit, onCancel: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(code) {
        while (code != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            if (code == null || expiresAt == null || now > expiresAt) {
                Text("Pair a computer", style = MaterialTheme.typography.titleMedium)
                Text(
                    "On your computer: pipx install \"git+https://github.com/UdaySoni32/OpenCell#subdirectory=cli\", then run: opencell pair <address>",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = onStart) { Text("Show pairing code") }
            } else {
                Text("Pairing code", style = MaterialTheme.typography.labelSmall)
                Text(code.chunked(3).joinToString(" "), fontSize = 40.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Expires in ${((expiresAt - now) / 1000).coerceAtLeast(0)}s", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: PairedDeviceEntity, vm: GatewayViewModel) {
    val scopes = Scope.parse(device.scopes)
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        device.lastSeenMillis?.let { "Last used ${relativeTime(it)}" } ?: "Never used",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { vm.revoke(device) }) { Text("Revoke", color = MaterialTheme.colorScheme.error) }
            }
            ScopeSwitch("Act without asking (auto-approve)", Scope.AUTO_APPROVE in scopes) { vm.toggleScope(device, Scope.AUTO_APPROVE, it) }
            ScopeSwitch("May approve requests", Scope.APPROVE in scopes) { vm.toggleScope(device, Scope.APPROVE, it) }
        }
    }
}

@Composable
private fun ScopeSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
