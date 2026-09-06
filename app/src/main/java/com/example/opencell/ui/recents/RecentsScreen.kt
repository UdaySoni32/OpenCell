package com.example.opencell.ui.recents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.opencell.domain.model.CallRecord
import com.example.opencell.domain.model.CallType
import com.example.opencell.ui.phone.DialPadGrid
import com.example.opencell.ui.phone.PhoneViewModel
import com.example.opencell.ui.theme.OpenCellTheme
import com.example.opencell.ui.theme.successColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home screen: recent calls with filters. The dialer is a feature here — a
 * "Dial" FAB opens a bottom-sheet dialpad — rather than a separate tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    viewModel: RecentsViewModel,
    phoneViewModel: PhoneViewModel,
    onRedialClick: (String) -> Unit = {},
    onMessageClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val isDialerOpen by viewModel.isDialerOpen.collectAsStateWithLifecycle()

    RecentsScreenContent(
        calls = calls,
        selectedFilter = selectedFilter,
        isDialerOpen = isDialerOpen,
        onFilterSelect = viewModel::setFilter,
        onDeleteCall = viewModel::deleteCall,
        onClearAll = viewModel::clearAllCalls,
        onOpenDialer = viewModel::openDialer,
        onCloseDialer = viewModel::closeDialer,
        phoneViewModel = phoneViewModel,
        onRedialClick = onRedialClick,
        onMessageClick = onMessageClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreenContent(
    calls: List<CallRecord>,
    selectedFilter: CallType?,
    isDialerOpen: Boolean,
    onFilterSelect: (CallType?) -> Unit,
    onDeleteCall: (CallRecord) -> Unit,
    onClearAll: () -> Unit,
    onOpenDialer: () -> Unit,
    onCloseDialer: () -> Unit,
    phoneViewModel: PhoneViewModel?,
    onRedialClick: (String) -> Unit,
    onMessageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Recents")
                    }
                },
                actions = {
                    if (calls.isNotEmpty()) {
                        IconButton(onClick = onClearAll) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear All History",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // Dialer: a feature on the home screen, not a tab.
            ExtendedFloatingActionButton(
                onClick = onOpenDialer,
                icon = { Icon(Icons.Default.Dialpad, contentDescription = null) },
                text = { Text("Dial") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { onFilterSelect(null) },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = selectedFilter == CallType.MISSED,
                    onClick = { onFilterSelect(CallType.MISSED) },
                    label = { Text("Missed") }
                )
                FilterChip(
                    selected = selectedFilter == CallType.INCOMING,
                    onClick = { onFilterSelect(CallType.INCOMING) },
                    label = { Text("Incoming") }
                )
                FilterChip(
                    selected = selectedFilter == CallType.OUTGOING,
                    onClick = { onFilterSelect(CallType.OUTGOING) },
                    label = { Text("Outgoing") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (calls.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Recent Calls",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tap Dial to start a call",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    // Keep the last rows tappable above the Dial FAB.
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(calls, key = { it.id }) { call ->
                        RecentCallItemCard(
                            call = call,
                            onRedial = { onRedialClick(call.phoneNumber) },
                            onDelete = { onDeleteCall(call) }
                        )
                    }
                }
            }
        }
    }

    // Dialer bottom sheet — the dialpad is a feature of the home screen.
    if (isDialerOpen && phoneViewModel != null) {
        DialerBottomSheet(
            phoneViewModel = phoneViewModel,
            onDismiss = onCloseDialer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerBottomSheet(
    phoneViewModel: PhoneViewModel,
    onDismiss: () -> Unit
) {
    val dialedNumber by phoneViewModel.dialedNumber.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = dialedNumber.ifEmpty { "Enter Number" },
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (dialedNumber.isEmpty()) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            DialPadGrid(
                onDigitClick = phoneViewModel::onDigitClick,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FloatingActionButton(
                    onClick = {
                        phoneViewModel.onCallClick()
                        onDismiss()
                    },
                    containerColor = successColor(),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RecentCallItemCard(
    call: CallRecord,
    onRedial: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val timeStr = remember(call.timestamp) { dateFormat.format(Date(call.timestamp)) }

    val (icon, tintColor, labelText) = when (call.callType) {
        CallType.INCOMING -> Triple(
            Icons.AutoMirrored.Filled.CallReceived,
            successColor(),
            "Incoming"
        )
        CallType.OUTGOING -> Triple(
            Icons.AutoMirrored.Filled.CallMade,
            MaterialTheme.colorScheme.primary,
            "Outgoing"
        )
        CallType.MISSED -> Triple(
            Icons.AutoMirrored.Filled.CallMissed,
            MaterialTheme.colorScheme.error,
            "Missed"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = tintColor.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = labelText,
                        tint = tintColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = call.contactName ?: call.phoneNumber,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (call.contactName != null) {
                    Text(
                        text = call.phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    if (call.durationSeconds > 0) {
                        Text(
                            text = " • ${call.durationSeconds / 60}m ${call.durationSeconds % 60}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRedial) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Redial",
                        tint = successColor()
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Record",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecentsScreenPreview() {
    OpenCellTheme {
        RecentsScreenContent(
            calls = listOf(
                CallRecord(
                    id = 1,
                    phoneNumber = "+1 (555) 012-3456",
                    contactName = "Alice Smith",
                    callType = CallType.INCOMING,
                    timestamp = System.currentTimeMillis() - 1800000,
                    durationSeconds = 142
                ),
                CallRecord(
                    id = 2,
                    phoneNumber = "+1 (800) 555-0100",
                    contactName = "Network Ops",
                    callType = CallType.MISSED,
                    timestamp = System.currentTimeMillis() - 3600000,
                    durationSeconds = 0
                )
            ),
            selectedFilter = null,
            isDialerOpen = false,
            onFilterSelect = {},
            onDeleteCall = {},
            onClearAll = {},
            onOpenDialer = {},
            onCloseDialer = {},
            phoneViewModel = null,
            onRedialClick = {},
            onMessageClick = {}
        )
    }
}
