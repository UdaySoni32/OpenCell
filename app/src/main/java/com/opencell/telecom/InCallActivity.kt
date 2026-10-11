package com.opencell.telecom

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.opencell.OpenCellApp
import com.opencell.ui.dialer.DialPad
import com.opencell.ui.formatDuration
import com.opencell.ui.theme.OpenCellTheme
import kotlinx.coroutines.delay

class InCallActivity : ComponentActivity() {

    private val calls get() = (application as OpenCellApp).container.callManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            OpenCellTheme {
                val active by calls.activeCalls.collectAsState()
                val audio by calls.audio.collectAsState()
                val call = active.firstOrNull { it.state == CallState.RINGING } ?: active.firstOrNull()
                LaunchedEffect(active.isEmpty()) { if (active.isEmpty()) finish() }
                if (call != null) InCallScreen(call, audio, calls)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_ANSWER) {
            intent.getStringExtra(CallActionReceiver.EXTRA_CALL_ID)?.let(calls::answer)
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.opencell.action.ANSWER"
    }
}

@Composable
private fun InCallScreen(call: ActiveCall, audio: AudioUi, calls: CallManager) {
    var showKeypad by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text(call.displayName, style = MaterialTheme.typography.headlineLarge)
            if (call.name != null && call.number != null) Text(call.number, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            CallStatusText(call)
            Spacer(Modifier.weight(1f))
            if (call.state == CallState.RINGING) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundAction(Icons.Filled.CallEnd, "Decline", MaterialTheme.colorScheme.error) { calls.reject(call.id) }
                    RoundAction(Icons.Filled.Call, "Answer", Color(0xFF1E8E3E)) { calls.answer(call.id) }
                }
            } else {
                if (showKeypad) {
                    DialPad(onKey = { calls.playDtmf(call.id, it) })
                    Spacer(Modifier.height(24.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Toggle(Icons.Filled.MicOff, "Mute", audio.isMuted) { calls.setMuted(!audio.isMuted) }
                    Toggle(Icons.AutoMirrored.Filled.VolumeUp, "Speaker", audio.isSpeaker) { calls.setSpeaker(!audio.isSpeaker) }
                    Toggle(Icons.Filled.Pause, "Hold", call.state == CallState.HOLDING) { calls.toggleHold(call.id) }
                    Toggle(Icons.Filled.Dialpad, "Keypad", showKeypad) { showKeypad = !showKeypad }
                }
                Spacer(Modifier.height(32.dp))
                RoundAction(Icons.Filled.CallEnd, "Hang up", MaterialTheme.colorScheme.error) { calls.hangup(call.id) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CallStatusText(call: ActiveCall) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call.connectedAtMillis) {
        while (call.connectedAtMillis != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val text = when (call.state) {
        CallState.RINGING -> "Incoming call"
        CallState.DIALING, CallState.CONNECTING -> "Calling…"
        CallState.HOLDING -> "On hold"
        CallState.DISCONNECTED -> "Call ended"
        CallState.ACTIVE -> call.connectedAtMillis?.let { formatDuration((now - it) / 1000) } ?: "Connected"
    }
    Text(text, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingActionButton(onClick = onClick, shape = CircleShape, containerColor = color, contentColor = Color.White, modifier = Modifier.size(72.dp)) {
            Icon(icon, contentDescription = label, Modifier.size(32.dp))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Toggle(icon: ImageVector, label: String, checked: Boolean, onToggle: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconToggleButton(checked = checked, onCheckedChange = { onToggle() }, modifier = Modifier.size(56.dp)) {
            Icon(icon, contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}
