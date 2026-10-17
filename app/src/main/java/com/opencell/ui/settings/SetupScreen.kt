package com.opencell.ui.settings

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.CALL_PHONE,
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.READ_CALL_LOG,
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.SEND_SMS,
    Manifest.permission.READ_SMS,
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.ANSWER_PHONE_CALLS,
    Manifest.permission.POST_NOTIFICATIONS,
)

data class SetupStatus(val dialer: Boolean, val sms: Boolean, val permissions: Boolean, val notificationAccess: Boolean) {
    val complete get() = dialer && sms && permissions && notificationAccess
}

fun setupStatus(context: Context): SetupStatus {
    val roles = context.getSystemService(RoleManager::class.java)
    return SetupStatus(
        dialer = roles.isRoleHeld(RoleManager.ROLE_DIALER),
        sms = roles.isRoleHeld(RoleManager.ROLE_SMS),
        permissions = REQUIRED_PERMISSIONS.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED },
        notificationAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName),
    )
}

@Composable
fun SetupScreen(
    onOpenGuide: () -> Unit,
    onOpenGateway: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenTasks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val status = remember(refresh) { setupStatus(context) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val roles = context.getSystemService(RoleManager::class.java)

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Setup", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp))
        Text(
            if (status.complete) "OpenCell is ready." else "OpenCell needs these to replace your Phone and SMS apps.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        SetupRow("Default phone app", "Handles every SIM call", status.dialer) {
            roleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        }
        SetupRow("Default SMS app", "Receives and sends your texts", status.sms) {
            roleLauncher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_SMS))
        }
        SetupRow("Permissions", "Calls, call log, contacts, SMS, notifications", status.permissions) {
            permLauncher.launch(REQUIRED_PERMISSIONS)
        }
        SetupRow("Notification access", "Shows WhatsApp, Discord and other app calls in your call list", status.notificationAccess) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        ListItem(
            leadingContent = { Icon(Icons.Filled.AutoAwesome, null) },
            headlineContent = { Text("AI assistant") },
            supportingContent = { Text("Your AI keys, SMS replies, call screening, memory") },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
            modifier = Modifier.clickable(onClick = onOpenAssistant),
        )
        ListItem(
            leadingContent = { Icon(Icons.Filled.Computer, null) },
            headlineContent = { Text("Computers & API") },
            supportingContent = { Text("Control this phone from the CLI, scripts and AI agents") },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
            modifier = Modifier.clickable(onClick = onOpenGateway),
        )
        ListItem(
            leadingContent = { Icon(Icons.Filled.Shield, null) },
            headlineContent = { Text("Permissions & activity") },
            supportingContent = { Text("What automation may do, approvals, and the activity log") },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
            modifier = Modifier.clickable(onClick = onOpenActivity),
        )
        ListItem(
            leadingContent = { Icon(Icons.Filled.CheckCircle, null) },
            headlineContent = { Text("Tasks & reminders") },
            supportingContent = { Text("Follow-ups and reminders from your assistant") },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
            modifier = Modifier.clickable(onClick = onOpenTasks),
        )
        ListItem(
            leadingContent = { Icon(Icons.AutoMirrored.Filled.HelpOutline, null) },
            headlineContent = { Text("Setup guide") },
            supportingContent = { Text("Walk through the setup steps again") },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
            modifier = Modifier.clickable(onClick = onOpenGuide),
        )
    }
}

@Composable
private fun SetupRow(title: String, subtitle: String, done: Boolean, onFix: () -> Unit) {
    ListItem(
        leadingContent = {
            Icon(
                if (done) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                null,
                tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            )
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { if (!done) FilledTonalButton(onClick = onFix) { Text("Allow") } },
    )
}
