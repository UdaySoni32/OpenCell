package io.opencell.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiDocsScreen(
    onBack: () -> Unit,
    serverPort: Int = 8900
) {
    val context = LocalContext.current
    val baseUrl = "http://127.0.0.1:$serverPort"
    val bearer = "Authorization: Bearer oc_test_YOUR_KEY"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API Documentation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item { DocSection("1. Getting Started") { DocText("OpenCell runs an embedded Ktor API server on port $serverPort."); DocText("Use ADB port forwarding:"); CodeBlock("adb reverse tcp:$serverPort tcp:$serverPort", context); Spacer(modifier = Modifier.height(8.dp)); DocText("Then visit $baseUrl") } }
            item { DocSection("2. Authentication") { DocText("All endpoints except /health require a Bearer token:"); CodeBlock("curl -H \"$bearer\" $baseUrl/v1/devices", context); Spacer(modifier = Modifier.height(8.dp)); DocText("Create API keys in Settings > API Keys, or use the Testing Dashboard.") } }
            item { DocSection("3. Health Check") { DocText("No authentication required:"); CodeBlock("curl $baseUrl/health", context); DocText("Response: {\"status\":\"ok\",\"version\":\"0.1.0-mvp\"}") } }
            item { DocSection("4. Device Endpoints") { ApiEndpoint("GET", "/v1/devices", "List all registered devices"); ApiEndpoint("GET", "/v1/devices/current", "Get current device info"); ApiEndpoint("GET", "/v1/devices/{id}/network", "Network info"); ApiEndpoint("GET", "/v1/devices/{id}/sim", "SIM card info"); ApiEndpoint("GET", "/v1/devices/{id}/capabilities", "Device capabilities") } }
            item { DocSection("5. Call Endpoints") { ApiEndpoint("GET", "/v1/calls", "List call history (?state=MISSED)"); ApiEndpoint("POST", "/v1/calls", "Initiate outbound call"); ApiEndpoint("GET", "/v1/calls/{id}", "Get call details"); ApiEndpoint("POST", "/v1/calls/{id}/answer", "Answer incoming call"); ApiEndpoint("POST", "/v1/calls/{id}/reject", "Reject incoming call"); ApiEndpoint("POST", "/v1/calls/{id}/hangup", "End active call"); ApiEndpoint("POST", "/v1/calls/{id}/hold", "Hold call"); ApiEndpoint("POST", "/v1/calls/{id}/resume", "Resume held call"); Spacer(modifier = Modifier.height(8.dp)); DocText("Example - place a call:", bold = true); CodeBlock("curl -X POST $baseUrl/v1/calls \\\n  -H \"$bearer\" \\\n  -H \"Content-Type: application/json\" \\\n  -d '{\"to\": \"+1234567890\"}'", context) } }
            item { DocSection("6. Message Endpoints") { ApiEndpoint("GET", "/v1/messages", "List all messages"); ApiEndpoint("POST", "/v1/messages", "Send SMS"); ApiEndpoint("GET", "/v1/messages/{id}", "Get message details"); ApiEndpoint("GET", "/v1/conversations", "List conversation threads"); ApiEndpoint("GET", "/v1/conversations/{id}/messages", "Get thread messages"); Spacer(modifier = Modifier.height(8.dp)); DocText("Example - send an SMS:", bold = true); CodeBlock("curl -X POST $baseUrl/v1/messages \\\n  -H \"$bearer\" \\\n  -H \"Content-Type: application/json\" \\\n  -d '{\"to\": \"+1234567890\", \"body\": \"Hello!\"}'", context) } }
            item { DocSection("7. Contact Endpoints") { ApiEndpoint("GET", "/v1/contacts", "List/search contacts (?q=search)"); ApiEndpoint("GET", "/v1/contacts/lookup?number=+1...", "Lookup by phone number") } }
            item { DocSection("8. Events & WebSocket") { ApiEndpoint("GET", "/v1/events", "Recent events (?device_id=...&limit=50)"); ApiEndpoint("WS", "/v1/events/stream", "Real-time event streaming"); Spacer(modifier = Modifier.height(8.dp)); DocText("WebSocket events: call.created, call.ended, message.received, etc.") } }
            item { DocSection("9. Management Endpoints") { ApiEndpoint("GET", "/v1/api-keys", "List API keys"); ApiEndpoint("POST", "/v1/api-keys", "Create API key"); ApiEndpoint("DELETE", "/v1/api-keys/{id}", "Revoke API key"); ApiEndpoint("GET", "/v1/projects", "List projects"); ApiEndpoint("GET", "/v1/audit-logs", "Audit trail") } }
            item { DocSection("10. Quick Start Script") { DocText("Copy and paste this to test the full API:"); val script = "#!/bin/bash\nBASE=$baseUrl\nKEY=oc_test_YOUR_KEY\n\n# Health check\ncurl \"${'$'}BASE/health\"\n\n# List devices\ncurl -H \"$bearer\" \"${'$'}BASE/v1/devices\"\n\n# List calls\ncurl -H \"$bearer\" \"${'$'}BASE/v1/calls\"\n\n# Send SMS\ncurl -X POST \"${'$'}BASE/v1/messages\" \\\n  -H \"$bearer\" \\\n  -H \"Content-Type: application/json\" \\\n  -d '{\"to\":\"+1234567890\",\"body\":\"Hello!\"}'\n\n# List conversations\ncurl -H \"$bearer\" \"${'$'}BASE/v1/conversations\"\n\n# Get contacts\ncurl -H \"$bearer\" \"${'$'}BASE/v1/contacts?q=john\""; CodeBlock(script, context) } }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DocSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) { Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp)); content() }
    }
}

@Composable
private fun DocText(text: String, bold: Boolean = false) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun ApiEndpoint(method: String, path: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = RoundedCornerShape(4.dp), color = when (method) { "GET" -> Color(0xFF4CAF50).copy(alpha = 0.15f); "POST" -> Color(0xFF2196F3).copy(alpha = 0.15f); "DELETE" -> Color(0xFFF44336).copy(alpha = 0.15f); "WS" -> Color(0xFF9C27B0).copy(alpha = 0.15f); else -> MaterialTheme.colorScheme.surfaceVariant }, modifier = Modifier.padding(end = 8.dp)) {
            Text(text = method, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = when (method) { "GET" -> Color(0xFF4CAF50); "POST" -> Color(0xFF2196F3); "DELETE" -> Color(0xFFF44336); "WS" -> Color(0xFF9C27B0); else -> MaterialTheme.colorScheme.onSurface }, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Column(modifier = Modifier.weight(1f)) { Text(path, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) }
    }
}

@Composable
private fun CodeBlock(code: String, context: Context) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Text(text = code, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
            IconButton(onClick = { clipboardManager.setPrimaryClip(ClipData.newPlainText("code", code)); Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
            }
        }
    }
}
