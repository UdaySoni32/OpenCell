package com.opencell.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.opencell.ai.AiSettings
import com.opencell.ai.SpeechConfig
import com.opencell.ai.SpeechKind
import com.opencell.ai.SpeechPreset
import com.opencell.voice.CallAudioModes
import com.opencell.voice.VoiceController
import com.opencell.voice.shizuku.ShizukuCallAudio

/** Voice-call settings: which audio mode works on this phone, speech engines, auto pickup, learning from calls. */
@Composable
fun VoiceSettingsSection(
    settings: AiSettings,
    voice: VoiceController,
    hasKey: (String) -> Boolean,
    onUpdate: ((AiSettings) -> AiSettings) -> Unit,
    onSaveKey: (slot: String, key: String) -> Unit,
) {
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val caps = remember(refresh) { voice.capabilities() }
    val shizuku = remember(refresh) { voice.shizuku.status() }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    Text("Voice calls", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(12.dp)) {
            val mode = CallAudioModes.forVoice(caps)
            Text(mode?.let { "AI voice ready: ${it.label}" } ?: "AI voice is off: allow the microphone or Shizuku", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (!caps.hasMicPermission) FilledTonalButton(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Allow microphone") }
                when (shizuku) {
                    ShizukuCallAudio.Status.NEEDS_PERMISSION -> FilledTonalButton(onClick = { voice.shizuku.requestPermission(); refresh++ }) { Text("Allow Shizuku") }
                    ShizukuCallAudio.Status.NOT_RUNNING -> Unit
                    ShizukuCallAudio.Status.READY -> Unit
                }
            }
            if (shizuku == ShizukuCallAudio.Status.NOT_RUNNING) {
                Text(
                    "For the best quality, install Shizuku (free, no root) and start it with Wireless debugging. The AI then hears the caller directly instead of through the speaker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
    SpeechCard("Speech-to-text", "stt", settings.stt, isTts = false, hasKey, onSave = { c, key -> onUpdate { it.copy(stt = c) }; if (key.isNotBlank()) onSaveKey("stt", key) })
    SpeechCard("Text-to-speech", "tts", settings.tts, isTts = true, hasKey, onSave = { c, key -> onUpdate { it.copy(tts = c) }; if (key.isNotBlank()) onSaveKey("tts", key) })
    ListItem(
        headlineContent = { Text("AI answers missed calls") },
        supportingContent = { Text("Picks up after ${settings.autoPickupSeconds}s if you don't") },
        trailingContent = { Switch(checked = settings.autoPickup, onCheckedChange = { on -> onUpdate { it.copy(autoPickup = on) } }) },
    )
    ListItem(
        headlineContent = { Text("Learn from calls I answer") },
        supportingContent = { Text("Transcribes your own calls to remember facts (needs Shizuku). Tell callers if the law requires it.") },
        trailingContent = { Switch(checked = settings.learnFromCalls, onCheckedChange = { on -> onUpdate { it.copy(learnFromCalls = on) } }) },
    )
}

@Composable
private fun SpeechCard(title: String, slot: String, config: SpeechConfig, isTts: Boolean, hasKey: (String) -> Boolean, onSave: (SpeechConfig, String) -> Unit) {
    var kind by rememberSaveable(config) { mutableStateOf(config.kind) }
    var url by rememberSaveable(config) { mutableStateOf(config.baseUrl) }
    var model by rememberSaveable(config) { mutableStateOf(config.model) }
    var voiceName by rememberSaveable(config) { mutableStateOf(config.voice) }
    var key by rememberSaveable(config) { mutableStateOf("") }
    Card(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                if (config.kind == SpeechKind.ON_DEVICE) "On this phone" else "${config.model} @ ${config.baseUrl}" + if (hasKey(slot)) " · key saved" else "",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SpeechPreset.entries.forEach { p ->
                    val c = if (isTts) p.tts else p.stt
                    AssistChip(onClick = { kind = c.kind; url = c.baseUrl; model = c.model; voiceName = c.voice }, label = { Text(p.label) })
                }
            }
            if (kind == SpeechKind.OPENAI_COMPATIBLE) {
                OutlinedTextField(url, { url = it }, label = { Text("Base URL (OpenAI-compatible)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (isTts) OutlinedTextField(voiceName, { voiceName = it }, label = { Text("Voice") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(key, { key = it }, label = { Text("API key (blank = keep / none)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            }
            FilledTonalButton(
                onClick = { onSave(SpeechConfig(kind, url.trim(), model.trim(), voiceName.trim()), key); key = "" },
                enabled = kind == SpeechKind.ON_DEVICE || (url.startsWith("http") && model.isNotBlank()),
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Save") }
        }
    }
}
