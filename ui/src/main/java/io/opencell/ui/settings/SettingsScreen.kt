package io.opencell.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import io.opencell.server.ApiServerService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onApiKeyClick: () -> Unit,
    onDeviceClick: () -> Unit,
    onCapabilitiesClick: () -> Unit,
    onDeveloperClick: () -> Unit,
    onRoleSetupClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var isServerRunning by remember {
        mutableStateOf(ApiServerService.serverInstance?.isRunning() == true)
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ── API Server ──
            item {
                SettingsSectionHeader("API Server")
            }
            item {
                SettingsCard {
                    // Server status toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isServerRunning) {
                                    ApiServerService.stop(context)
                                    isServerRunning = false
                                } else {
                                    ApiServerService.start(context)
                                    isServerRunning = true
                                }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = if (isServerRunning)
                                Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "API Server",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (isServerRunning) "Running on port 8900" else "Stopped",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isServerRunning)
                                    Color(0xFF4CAF50)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        Switch(
                            checked = isServerRunning,
                            onCheckedChange = {
                                if (isServerRunning) {
                                    ApiServerService.stop(context)
                                    isServerRunning = false
                                } else {
                                    ApiServerService.start(context)
                                    isServerRunning = true
                                }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsItem(
                        icon = Icons.Outlined.VpnKey,
                        title = "API Keys",
                        subtitle = "Manage keys for external access",
                        onClick = onApiKeyClick
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsItem(
                        icon = Icons.Outlined.Security,
                        title = "Server Health",
                        subtitle = "Check endpoint status",
                        onClick = onDeveloperClick
                    )
                }
            }

            // ── Phone & SMS ──
            item {
                SettingsSectionHeader("Phone & SMS")
            }
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Outlined.Phone,
                        title = "Default App Setup",
                        subtitle = "Set OpenCell as default phone and SMS app",
                        onClick = onRoleSetupClick
                    )
                }
            }

            // ── Device ──
            item {
                SettingsSectionHeader("Device")
            }
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Outlined.PhoneAndroid,
                        title = "Device Info",
                        subtitle = "Model, Android version, and status",
                        onClick = onDeviceClick
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Outlined.Checklist,
                        title = "Capabilities",
                        subtitle = "Check supported features",
                        onClick = onCapabilitiesClick
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Outlined.Tune,
                        title = "Remote Access",
                        subtitle = "Configure tunnels and proxies",
                        onClick = { }
                    )
                }
            }

            // ── Debug ──
            item {
                SettingsSectionHeader("Debug & Testing")
            }
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Outlined.BugReport,
                        title = "Testing Dashboard",
                        subtitle = "Debug tools and event emitter",
                        onClick = onDeveloperClick
                    )
                }
            }

            // ── About ──
            item {
                SettingsSectionHeader("About")
            }
            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Outlined.Info,
                        title = "OpenCell",
                        subtitle = "Version 0.1.0-mvp",
                        onClick = { }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Outlined.EventNote,
                        title = "Event Log",
                        subtitle = "View system events",
                        onClick = { }
                    )
                }
            }

            // Bottom spacer
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(title, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
