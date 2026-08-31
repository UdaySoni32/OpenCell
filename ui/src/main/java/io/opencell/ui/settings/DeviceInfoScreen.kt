package io.opencell.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.opencell.core.model.SimInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    onBack: () -> Unit,
    viewModel: DeviceInfoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Info") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::loadDeviceInfo) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    InfoSection("Device") {
                        InfoRow("Manufacturer", Build.MANUFACTURER)
                        InfoRow("Model", Build.MODEL)
                        InfoRow("Brand", Build.BRAND)
                        InfoRow("Device ID", uiState.device?.id ?: "Loading…")
                    }
                }

                item {
                    InfoSection("Android") {
                        InfoRow("Version", Build.VERSION.RELEASE)
                        InfoRow("SDK Level", Build.VERSION.SDK_INT.toString())
                        InfoRow("Security Patch", Build.VERSION.SECURITY_PATCH ?: "Unknown")
                        InfoRow("Build", Build.DISPLAY)
                    }
                }

                if (uiState.simInfoList.isNotEmpty()) {
                    item {
                        InfoSection("SIM / Subscriptions") {
                            uiState.simInfoList.forEachIndexed { index, sim ->
                                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                SimInfoCard(sim = sim, index = index)
                            }
                        }
                    }
                } else {
                    item {
                        InfoSection("SIM") {
                            Text(
                                text = "No SIM detected or READ_PHONE_STATE permission not granted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                uiState.networkInfo?.let { net ->
                    item {
                        InfoSection("Network") {
                            InfoRow("Type", net.type)
                            InfoRow("Available", net.isAvailable.toString())
                            InfoRow("Connected", net.isConnected.toString())
                            InfoRow("Roaming", net.isRoaming.toString())
                            net.signalStrength?.let { InfoRow("Signal (dBm)", "$it dBm") }
                            net.signalLevel?.let { InfoRow("Signal Level", it.toString()) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimInfoCard(sim: SimInfo, index: Int) {
    Text(
        text = "SIM ${index + 1} (Slot ${sim.slotIndex})",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    InfoRow("Carrier", sim.carrierName)
    InfoRow("Display Name", sim.displayName)
    sim.number?.let { if (it.isNotBlank()) InfoRow("Number", it) }
    sim.countryCode?.let { InfoRow("Country", it.uppercase()) }
    InfoRow("Subscription ID", sim.subscriptionId.toString())
    InfoRow("eSIM", sim.isEmbedded.toString())
}

@Composable
private fun InfoSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}
