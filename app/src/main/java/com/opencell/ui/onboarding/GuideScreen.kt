package com.opencell.ui.onboarding

import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.opencell.ui.settings.REQUIRED_PERMISSIONS
import com.opencell.ui.settings.SetupStatus
import com.opencell.ui.settings.setupStatus

/** One page of the first-run guide. [done] null = informational page without a check. */
private data class Step(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val action: String? = null,
    val optional: Boolean = false,
    val done: (SetupStatus) -> Boolean? = { null },
)

private val steps = listOf(
    Step(
        Icons.Filled.PhoneAndroid,
        "Welcome to OpenCell",
        "An open-source Phone and SMS app with one call list for SIM, WhatsApp, Discord and other calls — plus an API and an optional AI assistant you control.\n\nThis guide takes about a minute. Everything stays on your phone unless you connect a computer or an AI service.",
    ),
    Step(
        Icons.Filled.Call,
        "Make OpenCell your phone app",
        "Android only lets the default phone app show the call screen and answer calls. You can switch back any time in Android Settings › Apps › Default apps.",
        action = "Set as phone app",
        done = { it.dialer },
    ),
    Step(
        Icons.AutoMirrored.Filled.Chat,
        "Make OpenCell your SMS app",
        "The default SMS app is the only app that receives your texts and saves sent messages. Your existing messages stay where they are.",
        action = "Set as SMS app",
        done = { it.sms },
    ),
    Step(
        Icons.Filled.Lock,
        "Allow calls, contacts, SMS and notifications",
        "Needed to place calls, show your call history and contact names, send texts, and alert you about calls and messages.",
        action = "Allow",
        done = { it.permissions },
    ),
    Step(
        Icons.Filled.Notifications,
        "See WhatsApp, Discord and other app calls",
        "OpenCell reads the call notifications of your other apps (only call notifications, nothing is sent anywhere) so missed WhatsApp, Discord or Telegram calls show up in one list.",
        action = "Open notification access",
        optional = true,
        done = { it.notificationAccess },
    ),
    Step(
        Icons.Filled.AutoAwesome,
        "Optional: AI assistant",
        "Settings › AI assistant: add your own AI (your Ollama server, OpenAI, Claude, Gemini…). It can draft SMS replies, screen unknown callers and answer calls — and by default asks you before sending anything.",
        optional = true,
    ),
    Step(
        Icons.Filled.Computer,
        "Optional: control from a computer",
        "Settings › Computers & API: turn on the gateway, then on any computer (Windows, macOS, Linux, Raspberry Pi) run\n\npipx install \"git+https://github.com/UdaySoni32/OpenCell#subdirectory=cli\"\nopencell pair <phone-address>\n\nUse Tailscale to reach the phone from anywhere. Full guide: github.com/UdaySoni32/OpenCell/blob/main/docs/SETUP.md",
        optional = true,
    ),
    Step(Icons.Filled.CheckCircle, "You're all set", "Open Settings any time to change permissions, the AI assistant, or paired computers. You can replay this guide from Settings."),
)

@Composable
fun GuideScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    var index by rememberSaveable { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    val status = remember(refresh) { setupStatus(context) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val roles = context.getSystemService(RoleManager::class.java)
    val step = steps[index]
    val done = step.done(status)
    val isLast = index == steps.lastIndex

    fun runAction() = when (index) {
        1 -> roleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        2 -> roleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_SMS))
        3 -> permLauncher.launch(REQUIRED_PERMISSIONS)
        4 -> context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        else -> Unit
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Step ${index + 1} of ${steps.size}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                if (!isLast) TextButton(onClick = onFinish) { Text("Skip guide") }
            }
            Progress(index, steps.size)
            AnimatedContent(targetState = index, label = "guide-step", modifier = Modifier.weight(1f)) { i ->
                val s = steps[i]
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 32.dp)) {
                    Box(
                        Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Icon(s.icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Spacer(Modifier.height(24.dp))
                    Text(s.title, style = MaterialTheme.typography.headlineMedium)
                    if (s.optional) Text("OPTIONAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(s.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (step.action != null) {
                if (done == true) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Done", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    FilledTonalButton(onClick = ::runAction, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) { Text(step.action) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { index-- }, enabled = index > 0) { Text("Back") }
                Button(onClick = { if (isLast) onFinish() else index++ }) {
                    Text(
                        when {
                            isLast -> "Start using OpenCell"
                            step.action != null && done != true && step.optional -> "Not now"
                            step.action != null && done != true -> "Later"
                            else -> "Next"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Progress(index: Int, total: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(
                Modifier.weight(1f).height(4.dp).clip(CircleShape)
                    .background(if (i <= index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}
