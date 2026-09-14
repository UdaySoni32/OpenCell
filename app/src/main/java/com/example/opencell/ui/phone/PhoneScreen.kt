package com.example.opencell.ui.phone

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.opencell.domain.model.AggregatedCallRecord
import com.example.opencell.domain.model.CallSession
import com.example.opencell.domain.model.CallState
import com.example.opencell.domain.model.CallType
import com.example.opencell.domain.model.SpeedDialEntry
import com.example.opencell.ui.contacts.ContactStore
import com.example.opencell.ui.theme.CallGreenLight
import com.example.opencell.ui.theme.MissedRed
import com.example.opencell.ui.theme.OpenCellTheme
import com.example.opencell.ui.theme.successColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneScreen(
    viewModel: PhoneViewModel,
    onBack: (() -> Unit)? = null,
    onMessageClick: (String) -> Unit = {},
    onContactDetailsClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dialedNumber by viewModel.dialedNumber.collectAsStateWithLifecycle()
    val isDialpadExpanded by viewModel.isDialpadExpanded.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val activeCallSession by viewModel.activeCallSession.collectAsStateWithLifecycle()
    val isModemAvailable by viewModel.isModemAvailable.collectAsStateWithLifecycle()
    val isDefaultDialer by viewModel.isDefaultDialer.collectAsStateWithLifecycle()
    val speedDialEntries by viewModel.speedDialEntries.collectAsStateWithLifecycle()
    val aggregatedRecentCalls by viewModel.aggregatedRecentCalls.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshDefaultDialerStatus(context)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshDefaultDialerStatus(context)
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    PhoneScreenContent(
        dialedNumber = dialedNumber,
        isDialpadExpanded = isDialpadExpanded,
        searchQuery = searchQuery,
        selectedFilter = selectedFilter,
        activeCallSession = activeCallSession,
        isModemAvailable = isModemAvailable,
        isDefaultDialer = isDefaultDialer,
        speedDialEntries = speedDialEntries,
        aggregatedRecentCalls = aggregatedRecentCalls,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRequestSetDefaultDialer = {
            val intent = viewModel.getSetDefaultDialerIntent(context)
            roleLauncher.launch(intent)
        },
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onFilterSelect = viewModel::setFilter,
        onToggleDialpad = viewModel::toggleDialpad,
        onDigitClick = viewModel::onDigitClick,
        onBackspaceClick = viewModel::onBackspaceClick,
        onClearClick = viewModel::onClearClick,
        onCallClick = viewModel::onCallClick,
        onCallNumber = viewModel::callNumber,
        onAddContact = viewModel::addContact,
        onAssignSpeedDial = viewModel::assignSpeedDial,
        onRemoveSpeedDial = viewModel::removeSpeedDial,
        onDeleteCall = viewModel::deleteCall,
        onSimulateIncomingCall = { viewModel.simulateIncomingCall() },
        onHangupClick = viewModel::hangupCall,
        onMessageClick = onMessageClick,
        onContactDetailsClick = onContactDetailsClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhoneScreenContent(
    dialedNumber: String,
    isDialpadExpanded: Boolean,
    searchQuery: String = "",
    selectedFilter: CallType? = null,
    activeCallSession: CallSession?,
    isModemAvailable: Boolean,
    isDefaultDialer: Boolean = true,
    speedDialEntries: List<SpeedDialEntry> = emptyList(),
    aggregatedRecentCalls: List<AggregatedCallRecord> = emptyList(),
    snackbarHostState: SnackbarHostState,
    onBack: (() -> Unit)? = null,
    onRequestSetDefaultDialer: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onFilterSelect: (CallType?) -> Unit = {},
    onToggleDialpad: () -> Unit = {},
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit = { onBackspaceClick() },
    onCallClick: () -> Unit,
    onCallNumber: (String) -> Unit = {},
    onAddContact: (String, String, String?) -> Unit = { _, _, _ -> },
    onAssignSpeedDial: (Int, String) -> Unit = { _, _ -> },
    onRemoveSpeedDial: (Int) -> Unit = {},
    onDeleteCall: (AggregatedCallRecord) -> Unit = {},
    onSimulateIncomingCall: () -> Unit = {},
    onHangupClick: () -> Unit,
    onMessageClick: (String) -> Unit = {},
    onContactDetailsClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isAddContactOpen by remember { mutableStateOf(false) }
    var isEditSpeedDialOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Phone")
                    }
                },
                actions = {
                    AssistChip(
                        onClick = onSimulateIncomingCall,
                        label = { Text("Simulate Call") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onToggleDialpad,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isDialpadExpanded) Icons.Default.KeyboardHide else Icons.Default.Dialpad,
                    contentDescription = if (isDialpadExpanded) "Hide Keypad" else "Keypad"
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status Banners
                if (!isDefaultDialer) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Default Phone App Required",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Set OpenCell as Default Phone App for Custom In-Call UI",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onRequestSetDefaultDialer,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Set Default")
                            }
                        }
                    }
                }

                if (!isModemAvailable) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = "Cellular Modem Offline / Emulator Mode",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp)
                        )
                    }
                }

                // Top Search Bar: Pill-shaped OutlinedTextField (RoundedCornerShape(28.dp)) with surfaceContainerHigh background
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    placeholder = { Text("Search calls & contacts") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    } else null,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true
                )

                if (!isDialpadExpanded) {
                    // COLLAPSED MODE: Display Speed Dial & Unified Recents History List
                    SpeedDialAndRecentsList(
                        searchQuery = searchQuery,
                        selectedFilter = selectedFilter,
                        speedDialEntries = speedDialEntries,
                        aggregatedRecentCalls = aggregatedRecentCalls,
                        onFilterSelect = onFilterSelect,
                        onCallNumber = onCallNumber,
                        onMessageClick = onMessageClick,
                        onContactDetailsClick = onContactDetailsClick,
                        onDeleteCall = onDeleteCall,
                        onOpenEditSpeedDial = { isEditSpeedDialOpen = true },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // EXPANDED MODE: Display Number Input + Dialpad Grid
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(90.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dialedNumber.ifEmpty { "Enter Number" },
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = if (dialedNumber.length > 12) 26.sp else 34.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (dialedNumber.isEmpty()) MaterialTheme.colorScheme.outline
                                    else MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )

                            if (dialedNumber.isNotEmpty()) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .combinedClickable(
                                            onClick = onBackspaceClick,
                                            onLongClick = onClearClick
                                        )
                                        .semantics { contentDescription = "Delete Digit (long-press to clear)" },
                                    shape = CircleShape,
                                    color = Color.Transparent
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (dialedNumber.isNotEmpty()) {
                        AssistChip(
                            onClick = { isAddContactOpen = true },
                            label = { Text("Add to Contacts") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    DialPadGrid(
                        onDigitClick = onDigitClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.size(56.dp))

                        FloatingActionButton(
                            onClick = onCallClick,
                            containerColor = successColor(),
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call",
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        FloatingActionButton(
                            onClick = onToggleDialpad,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardHide,
                                contentDescription = "Hide Keypad",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            if (activeCallSession != null) {
                ActiveCallOverlay(
                    callSession = activeCallSession,
                    onHangup = onHangupClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (isAddContactOpen) {
        AddContactDialogPrefilled(
            prefilledPhone = dialedNumber,
            onDismiss = { isAddContactOpen = false },
            onAdd = { name, phone, carrier ->
                onAddContact(name, phone, carrier)
                isAddContactOpen = false
            }
        )
    }

    if (isEditSpeedDialOpen) {
        EditSpeedDialDialog(
            speedDialEntries = speedDialEntries,
            onDismiss = { isEditSpeedDialOpen = false },
            onAssignSlot = onAssignSpeedDial,
            onRemoveSlot = onRemoveSpeedDial
        )
    }
}

@Composable
fun SpeedDialAndRecentsList(
    searchQuery: String,
    selectedFilter: CallType?,
    speedDialEntries: List<SpeedDialEntry>,
    aggregatedRecentCalls: List<AggregatedCallRecord>,
    onFilterSelect: (CallType?) -> Unit,
    onCallNumber: (String) -> Unit,
    onMessageClick: (String) -> Unit,
    onContactDetailsClick: (String) -> Unit,
    onDeleteCall: (AggregatedCallRecord) -> Unit,
    onOpenEditSpeedDial: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // Speed Dial Section (Hide if search query is active)
        if (searchQuery.isBlank()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Speed Dial (1-9)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = onOpenEditSpeedDial) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit")
                    }
                }
            }

            if (speedDialEntries.isNotEmpty()) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(speedDialEntries, key = { "sd_${it.slot}" }) { entry ->
                            SpeedDialEntryCard(
                                entry = entry,
                                onCall = { onCallNumber(entry.phoneNumber) }
                            )
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "No Speed Dial slots set. Tap Edit above to assign slots.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }
            }
        }

        // Filter Chips for Recent Calls
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
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
        }

        // Unified Recent Calls List
        item {
            Text(
                text = "Recent Calls",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        if (aggregatedRecentCalls.isNotEmpty()) {
            items(aggregatedRecentCalls, key = { "rec_${it.id}" }) { aggregatedCall ->
                val onCallLambda = remember(aggregatedCall.phoneNumber, onCallNumber) { { onCallNumber(aggregatedCall.phoneNumber) } }
                val onMessageLambda = remember(aggregatedCall.phoneNumber, onMessageClick) { { onMessageClick(aggregatedCall.phoneNumber) } }
                val onContactDetailsLambda = remember(aggregatedCall.phoneNumber, onContactDetailsClick) { { onContactDetailsClick(aggregatedCall.phoneNumber) } }
                val onDeleteLambda = remember(aggregatedCall, onDeleteCall) { { onDeleteCall(aggregatedCall) } }

                UnifiedRecentCallItemCard(
                    call = aggregatedCall,
                    onCall = onCallLambda,
                    onMessage = onMessageLambda,
                    onContactDetails = onContactDetailsLambda,
                    onDelete = onDeleteLambda
                )
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No Recent Calls" else "No matching calls",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tap Keypad icon below to dial",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

/**
 * Expressive horizontal Speed Dial Entry Card (surfaceContainerLow, 48dp avatar, slot badge).
 */
@Composable
fun SpeedDialEntryCard(
    entry: SpeedDialEntry,
    onCall: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(104.dp)
            .clickable(onClick = onCall),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (entry.contactName ?: entry.phoneNumber).take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Surface(
                    modifier = Modifier.size(20.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${entry.slot}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = entry.contactName ?: entry.phoneNumber,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = entry.phoneNumber,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Unified Recent Call Card:
 * - Tonal card with surfaceContainerLow & 24.dp rounded corners
 * - 40dp avatar with contact initial / tint
 * - Color-coded direction icons (↙️ Green Incoming, ↗️ Blue Outgoing, ↙️ Red Missed)
 * - Repeat count pills for count > 1
 * - Formatted timestamp
 */
@Composable
fun UnifiedRecentCallItemCard(
    call: AggregatedCallRecord,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onContactDetails: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val timeStr = remember(call.timestamp) { dateFormat.format(Date(call.timestamp)) }

    val (icon, tintColor, labelText) = remember(call.callType) {
        when (call.callType) {
            CallType.INCOMING -> Triple(
                Icons.AutoMirrored.Filled.CallReceived,
                CallGreenLight,
                "Incoming"
            )
            CallType.OUTGOING -> Triple(
                Icons.AutoMirrored.Filled.CallMade,
                Color(0xFF1976D2),
                "Outgoing"
            )
            CallType.MISSED -> Triple(
                Icons.AutoMirrored.Filled.CallMissed,
                MissedRed,
                "Missed"
            )
        }
    }

    val initial = remember(call.contactName, call.phoneNumber) {
        (call.contactName ?: call.phoneNumber).take(1).uppercase()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onContactDetails),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = tintColor.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = tintColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = labelText,
                        tint = tintColor,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp)
                    )
                    Text(
                        text = call.contactName ?: call.phoneNumber,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (call.count > 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = "(${call.count})",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (call.contactName != null) {
                    Text(
                        text = call.phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
                IconButton(onClick = onCall, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call",
                        tint = successColor(),
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onMessage, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = "Message",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onContactDetails, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Contact Details",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EditSpeedDialDialog(
    speedDialEntries: List<SpeedDialEntry>,
    onDismiss: () -> Unit,
    onAssignSlot: (Int, String) -> Unit,
    onRemoveSlot: (Int) -> Unit
) {
    var selectedSlotForPicker by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Speed Dial Slots (1-9)") },
        shape = RoundedCornerShape(28.dp),
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items((1..9).toList(), key = { "sd_slot_$it" }) { slot ->
                    val existing = speedDialEntries.find { it.slot == slot }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(32.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$slot",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                if (existing != null) {
                                    Text(
                                        text = existing.contactName ?: existing.phoneNumber,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = existing.phoneNumber,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    Text(
                                        text = "Unassigned",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            Row {
                                TextButton(onClick = { selectedSlotForPicker = slot }) {
                                    Text(if (existing != null) "Change" else "Assign")
                                }
                                if (existing != null) {
                                    IconButton(onClick = { onRemoveSlot(slot) }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear Slot $slot",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )

    selectedSlotForPicker?.let { slot ->
        AssignSpeedDialPickerModal(
            slot = slot,
            onDismiss = { selectedSlotForPicker = null },
            onSelectNumber = { phone ->
                onAssignSlot(slot, phone)
                selectedSlotForPicker = null
            }
        )
    }
}

@Composable
fun AssignSpeedDialPickerModal(
    slot: Int,
    onDismiss: () -> Unit,
    onSelectNumber: (String) -> Unit
) {
    val contacts by ContactStore.contacts.collectAsStateWithLifecycle()
    var customPhone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Slot $slot") },
        shape = RoundedCornerShape(28.dp),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = customPhone,
                    onValueChange = { customPhone = it },
                    label = { Text("Enter Custom Phone Number") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (customPhone.isNotBlank()) {
                    Button(
                        onClick = { onSelectNumber(customPhone) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use Number: $customPhone")
                    }
                }

                Text(
                    text = "Or Select Contact:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(contacts, key = { it.id }) { contact ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectNumber(contact.phoneNumber) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = contact.name.ifBlank { contact.phoneNumber },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = contact.phoneNumber,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {}
    )
}

@Composable
fun AddContactDialogPrefilled(
    prefilledPhone: String,
    onDismiss: () -> Unit,
    onAdd: (String, String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf(prefilledPhone) }
    var carrier by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Save Contact")
            }
        },
        shape = RoundedCornerShape(28.dp),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = carrier,
                    onValueChange = { carrier = it },
                    label = { Text("Carrier Label (Optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, phone, carrier) },
                enabled = name.isNotBlank() && phone.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ActiveCallOverlay(
    callSession: CallSession,
    onHangup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val durationText = remember(callSession.durationSeconds) {
        String.format(Locale.US, "%02d:%02d", callSession.durationSeconds / 60, callSession.durationSeconds % 60)
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = when (callSession.state) {
                    CallState.RINGING -> MaterialTheme.colorScheme.tertiaryContainer
                    CallState.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ) {
                Text(
                    text = callSession.state.name,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = callSession.contactName ?: callSession.phoneNumber,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (callSession.state == CallState.ACTIVE) durationText else "Calling…",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )

            if (callSession.isSimulated) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "(Simulated Telephony Engine)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            val hangupRed = MaterialTheme.colorScheme.error
            FloatingActionButton(
                onClick = onHangup,
                containerColor = hangupRed,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "Hang up",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun DialPadGrid(
    onDigitClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dialPadKeys = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to "")
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        dialPadKeys.forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowKeys.forEach { (digit, letters) ->
                    DialPadButton(
                        digit = digit,
                        subText = letters,
                        onClick = { onDigitClick(digit) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialPadButton(
    digit: String,
    subText: String,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digit,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subText.isNotEmpty()) {
                Text(
                    text = subText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PhoneScreenPreview() {
    OpenCellTheme {
        PhoneScreenContent(
            dialedNumber = "+1 (555) 019-2831",
            isDialpadExpanded = false,
            activeCallSession = null,
            isModemAvailable = true,
            isDefaultDialer = false,
            snackbarHostState = remember { SnackbarHostState() },
            onRequestSetDefaultDialer = {},
            onToggleDialpad = {},
            onDigitClick = {},
            onBackspaceClick = {},
            onClearClick = {},
            onCallClick = {},
            onHangupClick = {}
        )
    }
}
