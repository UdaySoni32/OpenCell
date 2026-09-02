package io.opencell.app.call

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
 * Full-screen incoming call UI.
 *
 * Launched by [io.opencell.platform.telecom.OpenCellInCallService] when an incoming call is detected.
 * Shows caller info (name + number) with large Answer and Reject buttons.
 * Communicates back to CallEngine via platform EntryPoint.
 */
@AndroidEntryPoint
class IncomingCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_DISPLAY_NAME = "display_name"

        fun launch(context: Context, callId: String, phoneNumber: String, displayName: String?) {
            val intent = Intent(context, IncomingCallActivity::class.java).apply {
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

    // Ringtone + Vibration
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var audioManager: AudioManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Start ringtone and vibration
        startRingtoneAndVibration()

        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: run { finish(); return }
        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: ""
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext, PlatformEntryPoint::class.java
        )
        val callEngine = entryPoint.callEngine()

        // Resolve caller name from contacts if not provided
        val resolvedName = if (displayName.isNullOrBlank()) {
            CallerLookup.lookupContactName(this, phoneNumber)
        } else {
            displayName
        }

        setContent {
            OpenCellTheme {
                IncomingCallScreen(
                    callerName = resolvedName,
                    phoneNumber = phoneNumber,
                    onAnswer = {
                        stopRingtoneAndVibration()
                        scope.launch {
                            callEngine.answerCall(callId)
                            ActiveCallActivity.launch(
                                this@IncomingCallActivity,
                                callId, phoneNumber, resolvedName
                            )
                            finish()
                        }
                    },
                    onReject = {
                        stopRingtoneAndVibration()
                        scope.launch {
                            callEngine.rejectCall(callId)
                            finish()
                        }
                    }
                )
            }
        }
    }

    private fun startRingtoneAndVibration() {
        // Set audio to ring mode so ringtone plays even if phone is on vibrate
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager?.let { am ->
            previousAudioMode = am.mode
            am.mode = AudioManager.MODE_RINGTONE
        }

        // Play default ringtone
        val ringtoneUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(applicationContext, ringtoneUri)
        ringtone?.let { rt ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                rt.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            rt.isLooping = true
            rt.play()
        }

        // Vibrate in a repeating pattern: vibrate 1s, pause 1s
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val pattern = longArrayOf(0, 1000, 1000) // delay, vibrate, pause
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(
                    VibrationEffect.createWaveform(pattern, 0) // repeat index 0 = loop
                )
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, 0)
            }
        }
    }

    private fun stopRingtoneAndVibration() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        audioManager?.let { am ->
            am.mode = previousAudioMode
        }
    }

    override fun onDestroy() {
        stopRingtoneAndVibration()
        super.onDestroy()
    }
}

@Composable
private fun IncomingCallScreen(
    callerName: String?,
    phoneNumber: String,
    onAnswer: () -> Unit,
    onReject: () -> Unit
) {
    // Pulsing animation for the caller icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // "Incoming call" text fade
    val textAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "textAlpha"
    )

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
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // Incoming call label
            Text(
                text = "Incoming call",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF81C784).copy(alpha = textAlpha),
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Caller avatar circle with pulse
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .shadow(24.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Color(0xFF81C784).copy(alpha = pulseAlpha * 0.3f)
                    ),
                contentAlignment = Alignment.Center
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
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Caller name
            Text(
                text = callerName ?: "Unknown",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Phone number
            if (phoneNumber.isNotBlank()) {
                Text(
                    text = CallerLookup.formatPhoneNumber(phoneNumber),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF8B949E),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(80.dp))

            // Answer and Reject buttons
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
            ) {
                // Reject button
                CallActionButton(
                    icon = Icons.Default.Call,
                    label = "Decline",
                    backgroundColor = Color(0xFFF85149),
                    iconTint = Color.White,
                    onClick = onReject
                )

                // Answer button
                CallActionButton(
                    icon = Icons.Default.Call,
                    label = "Answer",
                    backgroundColor = Color(0xFF3FB950),
                    iconTint = Color.White,
                    onClick = onAnswer
                )
            }
        }
    }
}

@Composable
private fun CallActionButton(
    icon: ImageVector,
    label: String,
    backgroundColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = backgroundColor
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8B949E)
        )
    }
}
