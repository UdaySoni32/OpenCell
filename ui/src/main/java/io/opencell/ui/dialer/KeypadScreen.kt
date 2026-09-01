package io.opencell.ui.dialer

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeypadScreen(
    viewModel: DialerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Phone number display
            Text(
                text = uiState.phoneNumber.ifEmpty { " " },
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
                            onClick = { viewModel.onPhoneNumberChange(uiState.phoneNumber + key.digit) },
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
                        if (uiState.phoneNumber.isNotEmpty())
                            viewModel.onPhoneNumberChange(uiState.phoneNumber.dropLast(1))
                    },
                    enabled = uiState.phoneNumber.isNotEmpty(),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.Backspace,
                        contentDescription = "Backspace",
                        modifier = Modifier.size(28.dp),
                        tint = if (uiState.phoneNumber.isNotEmpty())
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
                            if (uiState.phoneNumber.isNotBlank())
                                Color(0xFF4CAF50)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable(enabled = uiState.phoneNumber.isNotBlank()) { viewModel.dial() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = "Call",
                        modifier = Modifier.size(32.dp),
                        tint = if (uiState.phoneNumber.isNotBlank())
                            Color.White
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                // Spacer to balance the row
                Spacer(modifier = Modifier.size(56.dp))
            }
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
