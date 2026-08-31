package io.opencell.ui.dialer

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.opencell.core.model.CallDirection
import io.opencell.core.model.CallState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    viewModel: DialerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "Keypad",
                            fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Recents",
                                fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal
                            )
                            if (uiState.recentCalls.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(8.dp)
                                ) {}
                            }
                        }
                    }
                )
            }

            when (selectedTab) {
                0 -> KeypadTab(
                    phoneNumber = uiState.phoneNumber,
                    onPhoneNumberChange = viewModel::onPhoneNumberChange,
                    onCall = { viewModel.dial() }
                )
                1 -> RecentsTab(
                    calls = uiState.recentCalls,
                    onCallClick = { number ->
                        viewModel.onPhoneNumberChange(number)
                        selectedTab = 0
                    },
                    onDialNumber = { number ->
                        viewModel.onPhoneNumberChange(number)
                        viewModel.dial()
                    }
                )
            }
        }
    }
}

@Composable
private fun KeypadTab(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    onCall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Phone number display
        Text(
            text = phoneNumber.ifEmpty { " " },
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 36.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.sp
            ),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .heightIn(min = 48.dp),
            maxLines = 1
        )

        // Dial pad
        val dialPad = listOf(
            listOf(DialKey("1", ""), DialKey("2", "ABC"), DialKey("3", "DEF")),
            listOf(DialKey("4", "GHI"), DialKey("5", "JKL"), DialKey("6", "MNO")),
            listOf(DialKey("7", "PQRS"), DialKey("8", "TUV"), DialKey("9", "WXYZ")),
            listOf(DialKey("*", ""), DialKey("0", "+"), DialKey("#", ""))
        )

        dialPad.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { key ->
                    DialPadButton(
                        digit = key.digit,
                        letters = key.letters,
                        onClick = { onPhoneNumberChange(phoneNumber + key.digit) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom action row: backspace + call button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Backspace
            IconButton(
                onClick = {
                    if (phoneNumber.isNotEmpty()) onPhoneNumberChange(phoneNumber.dropLast(1))
                },
                enabled = phoneNumber.isNotEmpty(),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Default.Backspace,
                    contentDescription = "Backspace",
                    modifier = Modifier.size(28.dp),
                    tint = if (phoneNumber.isNotEmpty())
                        MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            // Call button
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        if (phoneNumber.isNotBlank())
                            Color(0xFF4CAF50)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable(enabled = phoneNumber.isNotBlank()) { onCall() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = "Call",
                    modifier = Modifier.size(32.dp),
                    tint = if (phoneNumber.isNotBlank())
                        Color.White
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }

            // Spacer to balance the row
            Spacer(modifier = Modifier.size(56.dp))
        }
    }
}

private data class DialKey(val digit: String, val letters: String)

@Composable
private fun DialPadButton(
    digit: String,
    letters: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val backgroundColor by animateColorAsState(
        if (isPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else Color.Transparent,
        label = "dialBtnBg"
    )

    Column(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = digit,
            fontSize = 30.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (letters.isNotEmpty()) {
            Text(
                text = letters,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RecentsTab(
    calls: List<io.opencell.core.database.entity.CallEntity>,
    onCallClick: (String) -> Unit,
    onDialNumber: (String) -> Unit
) {
    if (calls.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    text = "Calls you make or receive will appear here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
        return
    }

    // Group calls by date
    val grouped = calls.groupBy { call ->
        val cal = Calendar.getInstance().apply { timeInMillis = call.startedAt }
        val now = Calendar.getInstance()
        when {
            cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> "Today"
            cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1 -> "Yesterday"
            cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) ->
                SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(call.startedAt))
            else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(call.startedAt))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
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
            items(dayCalls, key = { it.id }) { call ->
                CallHistoryItem(
                    call = call,
                    onClick = {
                        val number = if (call.direction == "INBOUND") call.fromNumber else call.toNumber
                        onCallClick(number)
                    },
                    onDialNumber = { number ->
                        onDialNumber(number)
                    }
                )
            }
        }
    }
}

@Composable
private fun CallHistoryItem(
    call: io.opencell.core.database.entity.CallEntity,
    onClick: () -> Unit,
    onDialNumber: (String) -> Unit
) {
    val number = if (call.direction == "INBOUND") call.fromNumber else call.toNumber
    val isInbound = call.direction == "INBOUND"
    val isMissed = call.state == CallState.MISSED.name

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
                number.ifBlank { "Unknown" },
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
                    Icon(
                        directionIcon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = { onDialNumber(number) }) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = "Call back",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
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
