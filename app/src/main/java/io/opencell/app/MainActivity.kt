package io.opencell.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import io.opencell.platform.roles.RoleManager
import io.opencell.ui.navigation.MainNavigation
import io.opencell.ui.theme.OpenCellTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var roleManager: RoleManager

    /** Tracks whether the role-setup screen should be visible. Updated after each role grant. */
    private var showRoleSetup = mutableStateOf(false)

    /** Snackbar message for role setup feedback */
    private var snackbarMessage = mutableStateOf<String?>(null)

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // After the user grants (or denies) the role, re-check and update UI
        showRoleSetup.value = !roleManager.isFullyDefault()
        if (roleManager.isFullyDefault()) {
            snackbarMessage.value = "Default app roles granted!"
        }
    }

    /** Launcher for opening system default apps settings as fallback */
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Re-check after returning from settings
        showRoleSetup.value = !roleManager.isFullyDefault()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initial check — show setup if roles are not yet granted
        showRoleSetup.value = !roleManager.isFullyDefault()

        setContent {
            OpenCellTheme {
                val shouldShowSetup by showRoleSetup
                val message by snackbarMessage

                if (shouldShowSetup) {
                    RoleSetupScreen(
                        isDialer = roleManager.isDefaultDialer(),
                        isSms = roleManager.isDefaultSmsApp(),
                        onRequestDialer = { requestDialerRole() },
                        onRequestSms = { requestSmsRole() },
                        onOpenSettings = { openDefaultAppsSettings() },
                        onSkip = { showRoleSetup.value = false },
                        snackbarMessage = message,
                        onDismissSnackbar = { snackbarMessage.value = null }
                    )
                } else {
                    MainNavigation()
                }
            }
        }

        checkAndRequestPermissions()
    }

    override fun onStart() {
        super.onStart()
        // Re-check on every resume — user might have changed the default in system settings
        showRoleSetup.value = !roleManager.isFullyDefault()
    }

    private fun requestDialerRole() {
        val intent = roleManager.createRequestDialerRoleIntent()
        if (intent != null) {
            roleRequestLauncher.launch(intent)
        } else {
            // Role request system unavailable — show fallback
            snackbarMessage.value = "Cannot request dialer role. Use 'Open Settings' to set manually."
        }
    }

    private fun requestSmsRole() {
        val intent = roleManager.createRequestSmsRoleIntent()
        if (intent != null) {
            roleRequestLauncher.launch(intent)
        } else {
            // Role request system unavailable — show fallback
            snackbarMessage.value = "Cannot request SMS role. Use 'Open Settings' to set manually."
        }
    }

    private fun openDefaultAppsSettings() {
        try {
            settingsLauncher.launch(roleManager.openDefaultAppsSettings())
        } catch (e: Exception) {
            snackbarMessage.value = "Could not open settings: ${e.message}"
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.READ_PHONE_STATE)
        }
        if (checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.READ_CALL_LOG)
        }
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.READ_CONTACTS)
        }
        if (checkSelfPermission(android.Manifest.permission.SEND_SMS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.SEND_SMS)
        }
        if (checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.RECEIVE_SMS)
        }
        if (checkSelfPermission(android.Manifest.permission.CALL_PHONE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.CALL_PHONE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            if (checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
        }

        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), 1001)
        }
    }
}

@Composable
private fun RoleSetupScreen(
    isDialer: Boolean,
    isSms: Boolean,
    onRequestDialer: () -> Unit,
    onRequestSms: () -> Unit,
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit,
    snackbarMessage: String?,
    onDismissSnackbar: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            onDismissSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = "OpenCell",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Phone & Messaging Gateway",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // ── Explanation ──
                Text(
                    text = "OpenCell needs to be your default phone and SMS app to handle calls and messages.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── Phone Role Card ──
                RoleCard(
                    title = "Phone App",
                    isChecked = isDialer,
                    onSetClick = onRequestDialer
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ── SMS Role Card ──
                RoleCard(
                    title = "SMS App",
                    isChecked = isSms,
                    onSetClick = onRequestSms
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── Manual Setup Fallback ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Manual Setup",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "If the buttons above don't work (some devices require manual setup):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Step-by-step instructions
                        InstructionStep(
                            number = "1",
                            text = "Tap the button below to open Default Apps settings"
                        )
                        InstructionStep(
                            number = "2",
                            text = "Tap 'Phone app' and select OpenCell"
                        )
                        InstructionStep(
                            number = "3",
                            text = "Tap 'SMS app' and select OpenCell"
                        )
                        InstructionStep(
                            number = "4",
                            text = "Press Back to return to OpenCell"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onOpenSettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Default Apps Settings")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ── Skip ──
                TextButton(onClick = onSkip) {
                    Text("Skip for now")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun RoleCard(
    title: String,
    isChecked: Boolean,
    onSetClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isChecked)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Icon(
                imageVector = if (isChecked)
                    androidx.compose.material.icons.Icons.Default.CheckCircle
                else androidx.compose.material.icons.Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isChecked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (isChecked) "Set as default" else "Not set as default",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isChecked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            if (!isChecked) {
                Button(onClick = onSetClick) {
                    Text("Set")
                }
            }
        }
    }
}

@Composable
private fun InstructionStep(number: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(20.dp),
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
