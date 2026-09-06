package com.example.opencell

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.opencell.data.local.preferences.ThemeMode
import com.example.opencell.gateway.service.GatewayServerService
import com.example.opencell.ui.components.AdaptiveNavigationShell
import com.example.opencell.ui.contacts.ContactsViewModel
import com.example.opencell.ui.developer.DeveloperViewModel
import com.example.opencell.ui.messages.MessagesViewModel
import com.example.opencell.ui.phone.PhoneViewModel
import com.example.opencell.ui.recents.RecentsViewModel
import com.example.opencell.ui.settings.SettingsViewModel
import com.example.opencell.ui.theme.OpenCellTheme

class MainActivity : ComponentActivity() {

    private val app by lazy { application as OpenCellApplication }

    private val phoneViewModel: PhoneViewModel by viewModels {
        PhoneViewModel.Factory(app.callRepository, app.callEngine)
    }

    private val messagesViewModel: MessagesViewModel by viewModels {
        MessagesViewModel.Factory(app.messageRepository, app.messageEngine)
    }

    private val contactsViewModel: ContactsViewModel by viewModels()

    private val recentsViewModel: RecentsViewModel by viewModels {
        RecentsViewModel.Factory(app.callRepository)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(app.settingsDataStore)
    }

    private val developerViewModel: DeveloperViewModel by viewModels {
        DeveloperViewModel.Factory(
            app.developerPreferencesRepository,
            app.callRepository,
            app.messageRepository
        )
    }

    // Holds a phone number requested through an external ACTION_DIAL / ACTION_VIEW
    // (tel:) intent. When OpenCell is the default dialer these intents must be
    // handled in-app instead of being handed off to another dialer.
    private val dialPadRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GatewayServerService.startService(this)
        enableEdgeToEdge()
        handleDialIntent(intent)

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColorEnabled by settingsViewModel.dynamicColorEnabled.collectAsStateWithLifecycle()

            val useDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            OpenCellTheme(
                darkTheme = useDarkTheme,
                dynamicColor = dynamicColorEnabled
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AdaptiveNavigationShell(
                        phoneViewModel = phoneViewModel,
                        messagesViewModel = messagesViewModel,
                        contactsViewModel = contactsViewModel,
                        recentsViewModel = recentsViewModel,
                        settingsViewModel = settingsViewModel,
                        developerViewModel = developerViewModel,
                        dialPadRequest = dialPadRequest.value
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDialIntent(intent)
    }

    private fun handleDialIntent(intent: Intent?) {
        val action = intent?.action ?: return
        val number = intent.data?.let { uri ->
            if (uri.scheme == "tel") Uri.decode(uri.schemeSpecificPart) else null
        } ?: return
        when (action) {
            Intent.ACTION_DIAL, Intent.ACTION_VIEW -> {
                // Show the in-app dialer with the number prefilled.
                dialPadRequest.value = number
            }
            Intent.ACTION_CALL -> {
                // Dial immediately from within OpenCell (no system dialer handoff).
                dialPadRequest.value = number
                app.callEngine.initiateCall(number)
            }
        }
    }
}
