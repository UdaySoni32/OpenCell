package com.example.opencell.ui.incall

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.opencell.domain.model.CallSession
import com.example.opencell.domain.model.CallState
import com.example.opencell.ui.theme.OpenCellTheme
import com.example.opencell.ui.theme.successColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InCallScreen(
    callSession: CallSession,
    onAnswerClick: () -> Unit,
    onRejectClick: () -> Unit,
    onHangupClick: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleHold: () -> Unit,
    onSendDtmf: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDtmfSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val durationText = remember(callSession.durationSeconds) {
        val mins = callSession.durationSeconds / 60
        val secs = callSession.durationSeconds % 60
        String.format("%02d:%02d", mins, secs)
    }

    val contactName = callSession.contactName
    val phoneNumber = callSession.phoneNumber
    val displayName = contactName ?: phoneNumber
    val initials = remember(displayName) {
        displayName.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
            .ifEmpty { "?" }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: State Badge & Timer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when (callSession.state) {
                        CallState.RINGING -> MaterialTheme.colorScheme.tertiaryContainer
                        CallState.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                        CallState.HELD -> MaterialTheme.colorScheme.secondaryContainer
                        CallState.DIALING -> MaterialTheme.colorScheme.surfaceContainerHigh
                        else -> MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Text(
                        text = when (callSession.state) {
                            CallState.RINGING -> if (callSession.isIncoming) "INCOMING CALL" else "RINGING"
                            CallState.ACTIVE -> "ACTIVE CALL"
                            CallState.HELD -> "CALL ON HOLD"
                            CallState.DIALING -> "DIALING..."
                            CallState.ENDING -> "ENDING CALL..."
                            CallState.ENDED -> "CALL ENDED"
                            else -> callSession.state.name
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = when (callSession.state) {
                            CallState.RINGING -> MaterialTheme.colorScheme.onTertiaryContainer
                            CallState.ACTIVE -> MaterialTheme.colorScheme.onPrimaryContainer
                            CallState.HELD -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (callSession.state == CallState.ACTIVE || callSession.state == CallState.HELD) durationText else "Connecting...",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Remote Contact Avatar & Info Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (contactName != null && initials.isNotEmpty()) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Contact Avatar",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )

                if (contactName != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = phoneNumber,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }

                if (callSession.isSimulated) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "(Simulated Telecom Mode)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // In-Call Controls & Action FABs Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (callSession.state != CallState.RINGING) {
                    // Active / Dialing / Held Control Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mute Control
                        InCallControlButton(
                            icon = if (callSession.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = if (callSession.isMuted) "Muted" else "Mute",
                            isActive = callSession.isMuted,
                            activeColor = MaterialTheme.colorScheme.error,
                            onClick = onToggleMute
                        )

                        // Speaker Control
                        InCallControlButton(
                            icon = if (callSession.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            label = if (callSession.isSpeakerOn) "Speaker" else "Earpiece",
                            isActive = callSession.isSpeakerOn,
                            activeColor = MaterialTheme.colorScheme.primary,
                            onClick = onToggleSpeaker
                        )

                        // Hold Control
                        InCallControlButton(
                            icon = if (callSession.isOnHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                            label = if (callSession.isOnHold) "Resume" else "Hold",
                            isActive = callSession.isOnHold,
                            activeColor = MaterialTheme.colorScheme.tertiary,
                            onClick = onToggleHold
                        )

                        // Keypad / DTMF Control
                        InCallControlButton(
                            icon = Icons.Default.Dialpad,
                            label = "Keypad",
                            isActive = showDtmfSheet,
                            activeColor = MaterialTheme.colorScheme.primary,
                            onClick = { showDtmfSheet = true }
                        )
                    }
                }

                // Call Action FABs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (callSession.state == CallState.RINGING && callSession.isIncoming) {
                        // Incoming: Red Decline & Green Answer FABs
                        FloatingActionButton(
                            onClick = onRejectClick,
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(76.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Decline Call",
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        FloatingActionButton(
                            onClick = onAnswerClick,
                            containerColor = successColor(),
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(76.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Answer Call",
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    } else {
                        // Active / Dialing / Outgoing: Prominent Red Hang Up FAB
                        FloatingActionButton(
                            onClick = onHangupClick,
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(76.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Hang Up",
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                }
            }
        }

        // DTMF Keypad Bottom Sheet
        if (showDtmfSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDtmfSheet = false },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "DTMF Keypad",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    InCallDtmfGrid(onKeyClick = { digit ->
                        onSendDtmf(digit)
                    })

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(onClick = { showDtmfSheet = false }) {
                        Text("Close Keypad")
                    }
                }
            }
        }
    }
}

@Composable
fun InCallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            shape = CircleShape,
            color = if (isActive) activeColor else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun InCallDtmfGrid(
    onKeyClick: (Char) -> Unit
) {
    val keys = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#')
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                row.forEach { char ->
                    Surface(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .clickable { onKeyClick(char) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = char.toString(),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun InCallScreenPreview() {
    OpenCellTheme {
        InCallScreen(
            callSession = CallSession(
                phoneNumber = "+1 (555) 019-2831",
                contactName = "Alice Smith",
                state = CallState.ACTIVE,
                durationSeconds = 83,
                isMuted = false,
                isSpeakerOn = true
            ),
            onAnswerClick = {},
            onRejectClick = {},
            onHangupClick = {},
            onToggleMute = {},
            onToggleSpeaker = {},
            onToggleHold = {},
            onSendDtmf = {}
        )
    }
}
