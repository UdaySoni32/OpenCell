package io.opencell.app.call

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import android.app.NotificationManager
import android.app.NotificationChannel
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.EntryPointAccessors
import io.opencell.platform.di.PlatformEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Demo screen for testing call and SMS functionality.
 *
 * Provides buttons to:
 * - Simulate an incoming call (launches IncomingCallActivity with demo data)
 * - Place an outgoing call (launches ActiveCallActivity with demo data)
 * - Send a test SMS via the API
 * - View the API server health
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apiStatus by remember { mutableStateOf<String?>(null) }
    var lastCallId by remember { mutableStateOf<String?>(null) }
    var smsStatus by remember { mutableStateOf<String?>(null) }

    val entryPoint = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, PlatformEntryPoint::class.java)
    }
    val callEngine = remember { entryPoint.callEngine() }
    val messagingEngine = remember { entryPoint.messagingEngine() }
    val deviceEngine = remember { entryPoint.deviceEngine() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call & SMS Demo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D1117)
                )
            )
        },
        containerColor = Color(0xFF0D1117)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header ──
            Text(
                text = "OpenCell Demo",
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFF81C784),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Test calling and SMS features without a real carrier.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8B949E)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Simulate Incoming Call ──
            DemoCard(
                title = "Incoming Call",
                description = "Simulates a phone call from 'John Smith' (+1 555-123-4567). Shows the custom incoming call screen with caller ID.",
                icon = Icons.Default.Phone,
                buttonLabel = "Simulate Incoming Call",
                buttonColor = Color(0xFF3FB950),
                onClick = {
                    val callId = "demo_call_${System.currentTimeMillis()}"
                    lastCallId = callId
                    IncomingCallActivity.launch(
                        context,
                        callId,
                        "+15551234567",
                        "John Smith"
                    )
                }
            )

            // ── Simulate Active Call ──
            DemoCard(
                title = "Active Call",
                description = "Shows the in-call UI with timer, mute, speaker, keypad, and end call controls.",
                icon = Icons.Default.Dialpad,
                buttonLabel = "Show Active Call UI",
                buttonColor = Color(0xFF2196F3),
                onClick = {
                    val callId = lastCallId ?: "demo_call_${System.currentTimeMillis()}"
                    ActiveCallActivity.launch(
                        context,
                        callId,
                        "+15551234567",
                        "John Smith"
                    )
                }
            )

            // ── Simulate Call from Unknown Number ──
            DemoCard(
                title = "Unknown Caller",
                description = "Simulates an incoming call from an unknown number — tests the fallback caller ID display.",
                icon = Icons.Default.Person,
                buttonLabel = "Simulate Unknown Caller",
                buttonColor = Color(0xFFFF9800),
                onClick = {
                    val callId = "demo_unknown_${System.currentTimeMillis()}"
                    IncomingCallActivity.launch(
                        context,
                        callId,
                        "+18005550199",
                        null
                    )
                }
            )

            // ── Send Test SMS via MessagingEngine ──
            DemoCard(
                title = "Send Test SMS",
                description = "Sends a test SMS to +1 555-987-6543 via MessagingEngine (same path as ComposeScreen).",
                icon = Icons.Default.Sms,
                buttonLabel = "Send Test SMS",
                buttonColor = Color(0xFF9C27B0),
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val deviceId = deviceEngine.getLocalDeviceId()
                            val result = messagingEngine.sendSms(
                                to = "+15559876543",
                                body = "Hello from OpenCell! Test SMS at ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}",
                                deviceId = deviceId
                            )
                            result.fold(
                                onSuccess = {
                                    smsStatus = "✅ SMS sent to +1 555-987-6543 (${it.state})"
                                },
                                onFailure = { e ->
                                    smsStatus = "❌ SMS failed: ${e.message}"
                                }
                            )
                        } catch (e: Exception) {
                            smsStatus = "❌ SMS failed: ${e.message}"
                        }
                    }
                }
            )

            // ── Simulate Incoming SMS ──
            DemoCard(
                title = "Simulate Incoming SMS",
                description = "Simulates receiving an SMS from +1 555-888-1234. Shows a notification with the message.",
                icon = Icons.Default.Notifications,
                buttonLabel = "Simulate Incoming SMS",
                buttonColor = Color(0xFFFF5722),
                onClick = {
                    val notifMgr = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
                    val channelId = "opencell_sms"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val channel = NotificationChannel(channelId, "Messages", NotificationManager.IMPORTANCE_HIGH)
                        channel.description = "Incoming SMS messages"
                        notifMgr.createNotificationChannels(listOf(channel))
                    }
                    val notification = NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("+1 555-888-1234")
                        .setContentText("Hey! Just testing the OpenCell SMS integration 📱")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build()
                    notifMgr.notify(9999, notification)
                    smsStatus = "✅ Incoming SMS notification shown from +1 555-888-1234"
                }
            )

            // ── API Health Check ──
            DemoCard(
                title = "API Health Check",
                description = "Tests the embedded Ktor API server running on port 8900.",
                icon = Icons.Default.Check,
                buttonLabel = "Check API Status",
                buttonColor = Color(0xFF607D8B),
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val url = java.net.URL("http://10.0.2.2:8900/health")
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            conn.connectTimeout = 3000
                            conn.readTimeout = 3000
                            val response = conn.inputStream.bufferedReader().readText()
                            apiStatus = "✅ $response"
                            conn.disconnect()
                        } catch (e: Exception) {
                            apiStatus = "❌ ${e.message}"
                        }
                    }
                }
            )

            // ── Status ──
            if (apiStatus != null || smsStatus != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF21262D)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (apiStatus != null) {
                            Text(
                                text = "API Status",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8B949E)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = apiStatus!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE6EDF3)
                            )
                        }
                        if (smsStatus != null) {
                            if (apiStatus != null) Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "SMS Status",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8B949E)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = smsStatus!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE6EDF3)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DemoCard(
    title: String,
    description: String,
    icon: ImageVector,
    buttonLabel: String,
    buttonColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(buttonColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = buttonColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8B949E),
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(buttonLabel, fontWeight = FontWeight.Medium)
            }
        }
    }
}
