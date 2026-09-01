package io.opencell.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.opencell.core.model.CallState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onContactClick: (String) -> Unit,
    onKeypadClick: () -> Unit,
    onRecentCallClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Phone",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onKeypadClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(
                    Icons.Default.Dialpad,
                    contentDescription = "Keypad",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // ── Favorites Section ──
            if (uiState.contacts.isNotEmpty()) {
                item(key = "favorites_header") {
                    SectionHeader("Favorites")
                }
                item(key = "favorites_row") {
                    FavoritesRow(
                        contacts = uiState.contacts.take(8),
                        onContactClick = onContactClick
                    )
                }
            }

            // ── Recents Section ──
            item(key = "recents_header") {
                SectionHeader("Recents")
            }

            if (uiState.recentCalls.isEmpty()) {
                item(key = "recents_empty") {
                    EmptyRecents(onKeypadClick = onKeypadClick)
                }
            } else {
                val grouped = uiState.recentCalls.groupBy { displayInfo ->
                    val cal = Calendar.getInstance().apply { timeInMillis = displayInfo.call.startedAt }
                    val now = Calendar.getInstance()
                    when {
                        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                        cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> "Today"
                        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                        cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1 -> "Yesterday"
                        cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
                            SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(displayInfo.call.startedAt))
                        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(displayInfo.call.startedAt))
                    }
                }

                grouped.forEach { (dateLabel, dayCalls) ->
                    item(key = "header_$dateLabel") {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                    items(dayCalls, key = { it.call.id }) { displayInfo ->
                        CallHistoryItem(
                            displayInfo = displayInfo,
                            onClick = {
                                val number = if (displayInfo.call.direction == "INBOUND")
                                    displayInfo.call.fromNumber else displayInfo.call.toNumber
                                onRecentCallClick(number)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun FavoritesRow(
    contacts: List<io.opencell.ui.home.ContactDisplayInfo>,
    onContactClick: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(contacts) { contactInfo ->
            FavoriteItem(
                contactInfo = contactInfo,
                onClick = {
                    val number = contactInfo.contact.phoneNumbers.firstOrNull()?.number
                    if (number != null) onContactClick(number)
                }
            )
        }
    }
}

@Composable
private fun FavoriteItem(
    contactInfo: ContactDisplayInfo,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = contactInfo.contact.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = contactInfo.contact.displayName.take(8),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EmptyRecents(onKeypadClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Phone,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No recent calls",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap the dialpad to make a call",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(onClick = onKeypadClick) {
                Icon(Icons.Default.Dialpad, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Keypad")
            }
        }
    }
}

@Composable
private fun CallHistoryItem(
    displayInfo: io.opencell.ui.dialer.CallDisplayInfo,
    onClick: () -> Unit
) {
    val call = displayInfo.call
    val number = if (call.direction == "INBOUND") call.fromNumber else call.toNumber
    val isInbound = call.direction == "INBOUND"
    val isMissed = call.state == CallState.MISSED.name
    val displayName = displayInfo.contactName ?: number.ifBlank { "Unknown" }

    val iconColor = when {
        isMissed -> MaterialTheme.colorScheme.error
        isInbound -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary
    }

    val directionIcon = when {
        isMissed -> Icons.Outlined.PhoneMissed
        isInbound -> Icons.Outlined.CallReceived
        else -> Icons.Outlined.CallMade
    }

    ListItem(
        headlineContent = {
            Text(
                displayName,
                fontWeight = FontWeight.Medium,
                color = if (isMissed)
                    MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    directionIcon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = iconColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    formatCallInfo(call),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (displayInfo.contactName != null) {
                        Text(
                            text = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = iconColor
                        )
                    } else {
                        Icon(
                            directionIcon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

private fun formatCallInfo(call: io.opencell.core.database.entity.CallEntity): String {
    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(call.startedAt))
    val durationStr = if (call.durationMs > 0) {
        val secs = call.durationMs / 1000
        if (secs >= 60) "${secs / 60}m ${secs % 60}s" else "${secs}s"
    } else ""
    return if (durationStr.isNotEmpty()) "$timeStr · $durationStr" else timeStr
}
