package io.opencell.app.call

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import io.opencell.platform.di.PlatformEntryPoint
import io.opencell.ui.theme.OpenCellTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen active call UI that coexists with the real system call.
 *
 * This activity does NOT manage audio — the system telecom framework handles that.
 * It provides a companion UI for the in-call controls and tracks the real call state
 * via PhoneStateListener to auto-finish when the call disconnects.
 */
@AndroidEntryPoint
class ActiveCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_DISPLAY_NAME = "display_name"

        fun launch(context: Context, callId: String, phoneNumber: String?, displayName: String?) {
            val intent = Intent(context, ActiveCallActivity::class.java).apply {
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_DISPLAY_NAME, displayName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
            context.startActivity(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private var telephonyManager: TelephonyManager? = null
    private var telecomManager: TelecomManager? = null
    private var callStartTime = 0L
    private var phoneStateListener: PhoneStateListener? = null
    private var hasFinished = false

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: run { finish(); return }
        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext, PlatformEntryPoint::class.java
        )
        val callEngine = entryPoint.callEngine()

        val resolvedName = if (displayName.isNullOrBlank()) {
            CallerLookup.lookupContactName(this, phoneNumber)
        } else {
            displayName
        }

        callStartTime = SystemClock.elapsedRealtime()

        // Track real call state — finish when call disconnects
        setupCallStateListener()

        setContent {
            OpenCellTheme {
                var callState by remember { mutableIntStateOf(TelephonyManager.CALL_STATE_OFFHOOK) }

                ActiveCallScreen(
                    callerName = resolvedName,
                    phoneNumber = phoneNumber,
                    callStartTime = callStartTime,
                    callState = callState,
                    onEndCall = {
                        endRealCall()
                    },
                    onMuteToggle = { muted ->
                        // Mute is handled by the system InCallUI — just update visual state
                    },
                    onSpeakerToggle = { speakerOn ->
                        // Speaker is handled by the system InCallUI
                    },
                    onHoldToggle = { held ->
                        scope.launch {
                            if (held) callEngine.holdCall(callId)
                            else callEngine.resumeCall(callId)
                        }
                    },
                    onSendDtmf = { digit ->
                        // DTMF tones require an active call through Telecom framework
                        Toast.makeText(this@ActiveCallActivity, "DTMF: $digit", Toast.LENGTH_SHORT).show()
                    },
                    onRecord = {
                        Toast.makeText(this@ActiveCallActivity, "Call recording coming soon", Toast.LENGTH_SHORT).show()
                    },
                    onAddCall = {
                        Toast.makeText(this@ActiveCallActivity, "Add call coming soon", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun setupCallStateListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager

        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_IDLE -> {
                        // Call ended — finish this activity
                        if (!hasFinished) {
                            hasFinished = true
                            finish()
                        }
                    }
                    TelephonyManager.CALL_STATE_RINGING -> {
                        // Another call coming in while in call
                    }
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        // Call is active/dialing
                    }
                }
            }
        }

        telephonyManager?.listen(
            phoneStateListener,
            PhoneStateListener.LISTEN_CALL_STATE
        )
    }

    /**
     * End the real system call via TelecomManager.
     * Falls back to finishing the activity if TelecomManager.endCall() fails.
     */
    private fun endRealCall() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val ended = telecomManager?.endCall() ?: false
                if (!ended) {
                    // TelecomManager.endCall() returned false — call may have already ended
                    // Just finish the activity
                    if (!hasFinished) {
                        hasFinished = true
                        finish()
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val tm = telephonyManager
                if (tm != null) {
                    // TelephonyManager.endCall() is deprecated but works on older APIs
                    val method = tm.javaClass.getDeclaredMethod("endCall")
                    method.isAccessible = true
                    method.invoke(tm)
                }
                if (!hasFinished) {
                    hasFinished = true
                    finish()
                }
            }
        } catch (e: Exception) {
            // Permission issue or other error — just finish
            if (!hasFinished) {
                hasFinished = true
                finish()
            }
        }
    }

    override fun onDestroy() {
        phoneStateListener?.let { telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE) }
        super.onDestroy()
    }
}

@Composable
private fun ActiveCallScreen(
    callerName: String?,
    phoneNumber: String,
    callStartTime: Long,
    callState: Int,
    onEndCall: () -> Unit,
    onMuteToggle: (Boolean) -> Unit,
    onSpeakerToggle: (Boolean) -> Unit,
    onHoldToggle: (Boolean) -> Unit,
    onSendDtmf: (String) -> Unit,
    onRecord: () -> Unit,
    onAddCall: () -> Unit
) {
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }
    var isHeld by remember { mutableStateOf(false) }
    var showKeypad by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = SystemClock.elapsedRealtime()
            elapsedSeconds = ((now - callStartTime) / 1000).toInt()
            delay(1000)
        }
    }

    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    val timerText = if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1117),
                        Color(0xFF161B22),
                        Color(0xFF0D1117)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            // Top section - caller info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 80.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1B5E20)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (!callerName.isNullOrBlank())
                            Icons.Default.Person else Icons.Default.Phone,
                        contentDescription = null,
                        tint = Color(0xFF81C784),
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = callerName ?: "Unknown",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (phoneNumber.isNotBlank()) {
                    Text(
                        text = CallerLookup.formatPhoneNumber(phoneNumber),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF8B949E)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = when {
                        isHeld -> "On Hold"
                        elapsedSeconds < 2 -> "Calling..."
                        else -> timerText
                    },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Light
                    ),
                    color = when {
                        isHeld -> Color(0xFFFFA726)
                        elapsedSeconds < 2 -> Color(0xFF81C784).copy(alpha = 0.7f)
                        else -> Color(0xFF81C784)
                    },
                    letterSpacing = 2.sp
                )
            }

            // Middle section - in-call controls + keypad overlay
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // DTMF Keypad overlay
                AnimatedVisibility(
                    visible = showKeypad,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    DtmfKeypad(onDigitClick = onSendDtmf)
                }

                if (!showKeypad) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        InCallControlButton(
                            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = "Mute",
                            isActive = isMuted,
                            onClick = {
                                isMuted = !isMuted
                                onMuteToggle(isMuted)
                            }
                        )
                        InCallControlButton(
                            icon = Icons.Default.Dialpad,
                            label = "Keypad",
                            isActive = false,
                            onClick = { showKeypad = true }
                        )
                        InCallControlButton(
                            icon = if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                            label = "Speaker",
                            isActive = isSpeaker,
                            onClick = {
                                isSpeaker = !isSpeaker
                                onSpeakerToggle(isSpeaker)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        InCallControlButton(
                            icon = if (isHeld) Icons.Default.PlayArrow else Icons.Default.Pause,
                            label = if (isHeld) "Resume" else "Hold",
                            isActive = isHeld,
                            onClick = {
                                isHeld = !isHeld
                                onHoldToggle(isHeld)
                            }
                        )
                        InCallControlButton(
                            icon = Icons.Default.PersonAdd,
                            label = "Add call",
                            isActive = false,
                            onClick = onAddCall
                        )
                        InCallControlButton(
                            icon = Icons.Default.FiberManualRecord,
                            label = "Record",
                            isActive = false,
                            onClick = onRecord
                        )
                    }
                }
            }

            // Bottom section - end call / close keypad
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 48.dp)
            ) {
                if (showKeypad) {
                    FilledTonalButton(
                        onClick = { showKeypad = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF21262D)
                        )
                    ) {
                        Icon(Icons.Default.KeyboardHide, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Close Keypad", color = Color(0xFFE6EDF3))
                    }
                } else {
                    FilledIconButton(
                        onClick = onEndCall,
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFFF85149)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "End call",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "End call",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8B949E)
                    )
                }
            }
        }
    }
}

@Composable
private fun DtmfKeypad(onDigitClick: (String) -> Unit) {
    val dtmfRows = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to "")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        dtmfRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { (digit, letters) ->
                    DtmfButton(
                        digit = digit,
                        letters = letters,
                        onClick = { onDigitClick(digit) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DtmfButton(
    digit: String,
    letters: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .padding(horizontal = 3.dp, vertical = 2.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = digit,
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
            color = Color(0xFFE6EDF3)
        )
        if (letters.isNotEmpty()) {
            Text(
                text = letters,
                fontSize = 9.sp,
                letterSpacing = 2.sp,
                color = Color(0xFF8B949E)
            )
        } else {
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun InCallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isActive)
                    Color(0xFF81C784).copy(alpha = 0.3f)
                else
                    Color(0xFF21262D)
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color(0xFF81C784) else Color(0xFFE6EDF3),
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) Color(0xFF81C784) else Color(0xFF8B949E)
        )
    }
}
