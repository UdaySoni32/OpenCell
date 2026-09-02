package io.opencell.ui.navigation

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import dagger.hilt.android.EntryPointAccessors
import io.opencell.platform.di.PlatformEntryPoint
import io.opencell.ui.TestingDashboard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Navigation routes for the OpenCell app.
 */
object Routes {
    const val HOME = "home"
    const val KEYPAD = "keypad"
    const val MESSAGES = "messages"
    const val CONVERSATION = "conversation/{threadId}"
    const val COMPOSE_MESSAGE = "compose"
    const val CONTACTS = "contacts"
    const val SETTINGS = "settings"
    const val SETTINGS_API_KEYS = "settings/api-keys"
    const val SETTINGS_DEVICE = "settings/device"
    const val SETTINGS_CAPABILITIES = "settings/capabilities"
    const val INCOMING_CALL = "incoming-call/{callId}"
    const val TESTING_DASHBOARD = "testing-dashboard"
    const val API_DOCS = "api-docs"

    fun conversation(threadId: String) = "conversation/$threadId"
    fun incomingCall(callId: String) = "incoming-call/$callId"
}

/**
 * Bottom navigation items — 4 tabs.
 * Home = Favorites + Recents, Keypad, Messages, Contacts.
 */
enum class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME(Routes.HOME, "Home", Icons.Default.Home),
    KEYPAD(Routes.KEYPAD, "Keypad", Icons.Default.Dialpad),
    MESSAGES(Routes.MESSAGES, "Messages", Icons.Default.ChatBubble),
    CONTACTS(Routes.CONTACTS, "Contacts", Icons.Default.Contacts)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in BottomNavItem.entries.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    BottomNavItem.entries.forEach { item ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    item.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                            },
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        val scope = rememberCoroutineScope()

        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Routes.HOME) {
                io.opencell.ui.home.HomeScreen(
                    onContactClick = { number ->
                        scope.launch {
                            launchCallUI(navController.context, number)
                        }
                    },
                    onKeypadClick = {
                        navController.navigate(Routes.KEYPAD) {
                            launchSingleTop = true
                        }
                    },
                    onRecentCallClick = { number ->
                        scope.launch {
                            launchCallUI(navController.context, number)
                        }
                    },
                    onSettingsClick = {
                        navController.navigate(Routes.SETTINGS)
                    }
                )
            }
            composable(Routes.KEYPAD) {
                io.opencell.ui.dialer.KeypadScreen()
            }
            composable(Routes.MESSAGES) {
                io.opencell.ui.messages.MessagesScreen(
                    onConversationClick = { threadId ->
                        navController.navigate(Routes.conversation(threadId))
                    },
                    onComposeClick = {
                        navController.navigate(Routes.COMPOSE_MESSAGE)
                    }
                )
            }
            composable(
                Routes.CONVERSATION,
                arguments = listOf(navArgument("threadId") { type = NavType.StringType })
            ) {
                io.opencell.ui.messages.ConversationScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.COMPOSE_MESSAGE) {
                io.opencell.ui.messages.ComposeScreen(
                    onBack = { navController.popBackStack() },
                    onSent = { navController.popBackStack() }
                )
            }
            composable(Routes.CONTACTS) {
                io.opencell.ui.contacts.ContactsScreen(
                    onContactClick = { number ->
                        scope.launch {
                            launchCallUI(navController.context, number)
                        }
                    },
                    onSmsClick = { number, name ->
                        navController.navigate(Routes.COMPOSE_MESSAGE)
                    }
                )
            }
            composable(Routes.SETTINGS) {
                val context = LocalContext.current
                io.opencell.ui.settings.SettingsScreen(
                    onApiKeyClick = { navController.navigate(Routes.SETTINGS_API_KEYS) },
                    onDeviceClick = { navController.navigate(Routes.SETTINGS_DEVICE) },
                    onCapabilitiesClick = { navController.navigate(Routes.SETTINGS_CAPABILITIES) },
                    onDeveloperClick = { navController.navigate(Routes.TESTING_DASHBOARD) },
                    onApiDocsClick = { navController.navigate(Routes.API_DOCS) },
                    onRoleSetupClick = {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.w("Navigation", "Could not open default apps settings", e)
                        }
                    }
                )
            }
            composable(Routes.SETTINGS_API_KEYS) {
                io.opencell.ui.settings.ApiKeysScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS_DEVICE) {
                io.opencell.ui.settings.DeviceInfoScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS_CAPABILITIES) {
                io.opencell.ui.settings.CapabilitiesScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.TESTING_DASHBOARD) {
                TestingDashboard(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.API_DOCS) {
                io.opencell.ui.settings.ApiDocsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * Initiate a call through CallEngine and launch ActiveCallActivity.
 * This creates the call record in the DB, emits events, and launches
 * the system telephony intent. ActiveCallActivity shows on top.
 */
suspend fun launchCallUI(context: android.content.Context, phoneNumber: String) {
    try {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, PlatformEntryPoint::class.java
        )
        val callEngine = entryPoint.callEngine()
        val deviceEngine = entryPoint.deviceEngine()
        val device = deviceEngine.getOrCreateLocalDevice()
        
        Log.i("Navigation", "Initiating real call to $phoneNumber")
        
        // makeCall() places the REAL call via ACTION_CALL intent + creates DB record
        val result = callEngine.makeCall(
            phoneNumber = phoneNumber,
            deviceId = device.id
        )
        
        val callId = result.getOrNull()?.id ?: "outgoing_${System.currentTimeMillis()}"
        
        // Lookup contact name using ContentResolver directly (avoid app module dependency)
        val displayName = try {
            val cursor = context.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                "${android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER} = ?",
                arrayOf(phoneNumber),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else phoneNumber
            } ?: phoneNumber
        } catch (e: Exception) { phoneNumber }
        
        Log.i("Navigation", "Call initiated: callId=$callId, result=${result.isSuccess}")
        
        // Small delay so the system telephony intent fires first
        delay(500)
        
        // Launch our custom ActiveCallActivity as a companion UI
        // Use Intent to avoid direct dependency on app module
        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber"))
        callIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        
        // Fire the call intent again in case the first one was consumed by system
        try { context.startActivity(callIntent) } catch (_: Exception) {}
        
        // Launch ActiveCallActivity via reflection to avoid circular dependency
        try {
            val activityClass = Class.forName("io.opencell.app.call.ActiveCallActivity")
            val launchMethod = activityClass.getMethod(
                "launch",
                android.content.Context::class.java,
                String::class.java,
                String::class.java,
                String::class.java
            )
            launchMethod.invoke(null, context, callId, phoneNumber, displayName)
        } catch (e: Exception) {
            Log.w("Navigation", "Could not launch ActiveCallActivity via reflection", e)
        }
    } catch (e: Exception) {
        Log.w("Navigation", "Failed to launch call via CallEngine, falling back to system dialer", e)
        // Fallback: open system dialer
        val fallback = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
        fallback.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(fallback)
    }
}
