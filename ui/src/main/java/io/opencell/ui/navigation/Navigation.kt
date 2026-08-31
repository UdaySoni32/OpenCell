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
import io.opencell.ui.TestingDashboard

/**
 * Navigation routes for the OpenCell app.
 */
object Routes {
    const val DIALER = "dialer"
    const val RECENTS = "recents"
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

    fun conversation(threadId: String) = "conversation/$threadId"
    fun incomingCall(callId: String) = "incoming-call/$callId"
}

/**
 * Bottom navigation items.
 */
enum class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    DIALER(Routes.DIALER, "Phone", Icons.Default.Phone),
    MESSAGES(Routes.MESSAGES, "Messages", Icons.Default.ChatBubble),
    CONTACTS(Routes.CONTACTS, "Contacts", Icons.Default.Contacts),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Default.Settings)
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
                                        popUpTo(Routes.DIALER) { saveState = true }
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
        NavHost(
            navController = navController,
            startDestination = Routes.DIALER,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Routes.DIALER) {
                io.opencell.ui.dialer.DialerScreen()
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
                val context = LocalContext.current
                io.opencell.ui.contacts.ContactsScreen(
                    onContactClick = { number ->
                        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
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
        }
    }
}
