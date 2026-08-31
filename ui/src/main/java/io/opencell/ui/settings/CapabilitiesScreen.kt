package io.opencell.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.opencell.core.model.Capability
import io.opencell.core.model.CapabilityStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapabilitiesScreen(
    onBack: () -> Unit,
    viewModel: CapabilitiesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var expandedCapability by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capabilities") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Summary row
                val supported = uiState.capabilities.count { it.status == CapabilityStatus.SUPPORTED }
                val total = uiState.capabilities.size
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = "$supported / $total capabilities supported",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                items(uiState.capabilities, key = { it.capability }) { capability ->
                    CapabilityItem(
                        capability = capability,
                        isExpanded = expandedCapability == capability.capability,
                        onToggle = {
                            expandedCapability = if (expandedCapability == capability.capability)
                                null else capability.capability
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityItem(
    capability: Capability,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val statusColor = when (capability.status) {
        CapabilityStatus.SUPPORTED -> Color(0xFF4CAF50)
        CapabilityStatus.UNSUPPORTED -> Color(0xFFF44336)
        CapabilityStatus.PERMISSION_REQUIRED -> Color(0xFFFFC107)
        CapabilityStatus.EXPERIMENTAL -> Color(0xFFFF9800)
        CapabilityStatus.DEVICE_DEPENDENT -> Color(0xFF9C27B0)
        CapabilityStatus.OEM_DEPENDENT -> Color(0xFF9C27B0)
        CapabilityStatus.CARRIER_DEPENDENT -> Color(0xFF2196F3)
        CapabilityStatus.UNKNOWN -> Color(0xFF9E9E9E)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(10.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = statusColor
                ) {}
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = capability.capability,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = capability.status.name.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
                if (capability.reason.isNotEmpty()) {
                    IconButton(onClick = onToggle) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Details",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            if (isExpanded && capability.reason.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = capability.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 20.dp)
                )
            }
        }
    }
}
