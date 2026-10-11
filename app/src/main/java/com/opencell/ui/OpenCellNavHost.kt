package com.opencell.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.opencell.ui.calls.CallsScreen
import com.opencell.ui.contacts.ContactsScreen
import com.opencell.ui.dialer.DialerScreen
import com.opencell.ui.messages.ConversationScreen
import com.opencell.ui.messages.ThreadsScreen
import com.opencell.ui.settings.SetupScreen
import kotlinx.coroutines.launch

object Routes {
    const val CALLS = "calls"
    const val MESSAGES = "messages"
    const val CONTACTS = "contacts"
    const val SETUP = "setup"
    const val DIALER = "dialer?number={number}"
    const val CONVERSATION = "conversation?threadId={threadId}&address={address}"

    fun dialer(number: String = "") = "dialer?number=${Uri.encode(number)}"
    fun conversation(threadId: Long?, address: String) = "conversation?threadId=${threadId ?: -1}&address=${Uri.encode(address)}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.CALLS, "Calls", Icons.Filled.History),
    Tab(Routes.MESSAGES, "Messages", Icons.AutoMirrored.Filled.Chat),
    Tab(Routes.CONTACTS, "Contacts", Icons.Filled.Contacts),
    Tab(Routes.SETUP, "Settings", Icons.Filled.Settings),
)

@Composable
fun OpenCellNavHost(startRoute: String, pendingRoute: String?, onRouteConsumed: () -> Unit, placeCall: (String) -> Boolean) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showChrome = tabs.any { it.route == current }
    val message: (String) -> Unit = { scope.launch { snackbar.showSnackbar(it) } }
    val call: (String) -> Unit = { if (!placeCall(it)) message("Can't place call — check Setup") }

    LaunchedEffect(pendingRoute) {
        pendingRoute?.let {
            nav.navigate(it)
            onRouteConsumed()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (current == Routes.CALLS || current == Routes.CONTACTS) {
                FloatingActionButton(onClick = { nav.navigate(Routes.dialer()) }) { Icon(Icons.Filled.Dialpad, "Dial") }
            } else if (current == Routes.MESSAGES) {
                FloatingActionButton(onClick = { nav.navigate(Routes.conversation(null, "")) }) { Icon(Icons.AutoMirrored.Filled.Chat, "New message") }
            }
        },
        bottomBar = {
            if (showChrome) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = startRoute, modifier = Modifier.padding(padding)) {
            composable(Routes.CALLS) { CallsScreen(onMessage = message) }
            composable(Routes.MESSAGES) { ThreadsScreen(onOpen = { id, addr -> nav.navigate(Routes.conversation(id, addr)) }) }
            composable(Routes.CONTACTS) {
                ContactsScreen(onCall = call, onMessage = { nav.navigate(Routes.conversation(null, it)) })
            }
            composable(Routes.SETUP) { SetupScreen() }
            composable(Routes.DIALER, arguments = listOf(navArgument("number") { type = NavType.StringType; defaultValue = "" })) {
                DialerScreen(initialNumber = it.arguments?.getString("number").orEmpty(), onCall = call)
            }
            composable(
                Routes.CONVERSATION,
                arguments = listOf(
                    navArgument("threadId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("address") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                val threadId = it.arguments?.getLong("threadId")?.takeIf { id -> id >= 0 }
                ConversationScreen(
                    threadId = threadId,
                    address = it.arguments?.getString("address").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onCall = call,
                )
            }
        }
    }
}
