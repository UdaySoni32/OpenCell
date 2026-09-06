package com.example.opencell.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.opencell.navigation.NavRoute
import com.example.opencell.ui.contacts.ContactsScreen
import com.example.opencell.ui.contacts.ContactsViewModel
import com.example.opencell.ui.developer.DeveloperScreen
import com.example.opencell.ui.developer.DeveloperViewModel
import com.example.opencell.ui.messages.MessagesScreen
import com.example.opencell.ui.messages.MessagesViewModel
import com.example.opencell.ui.phone.PhoneScreen
import com.example.opencell.ui.phone.PhoneViewModel
import com.example.opencell.ui.recents.RecentsScreen
import com.example.opencell.ui.recents.RecentsViewModel
import com.example.opencell.ui.settings.SettingsScreen
import com.example.opencell.ui.settings.SettingsViewModel

data class NavTabItem(
    val route: NavRoute,
    val title: String,
    val icon: ImageVector
)

val navTabItems = listOf(
    NavTabItem(NavRoute.Phone, "Phone", Icons.Default.Phone),
    NavTabItem(NavRoute.Messages, "Messages", Icons.AutoMirrored.Filled.Message),
    NavTabItem(NavRoute.Contacts, "Contacts", Icons.Default.Person),
    NavTabItem(NavRoute.Recents, "Recents", Icons.Default.History),
    NavTabItem(NavRoute.Settings, "Settings", Icons.Default.Settings),
    NavTabItem(NavRoute.Developer, "Dev", Icons.Default.BugReport)
)

@Composable
fun AdaptiveNavigationShell(
    phoneViewModel: PhoneViewModel,
    messagesViewModel: MessagesViewModel,
    contactsViewModel: ContactsViewModel,
    recentsViewModel: RecentsViewModel,
    settingsViewModel: SettingsViewModel,
    developerViewModel: DeveloperViewModel,
    dialPadRequest: String? = null,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf<NavRoute>(NavRoute.Phone) }
    val backStack = rememberNavBackStack(NavRoute.Phone)

    val onNavigateToTab: (NavRoute) -> Unit = { tab ->
        currentTab = tab
        if (backStack.lastOrNull() != tab) {
            backStack.add(tab)
        }
    }

    // Incoming ACTION_DIAL / tel: intents (e.g. tapping a phone link elsewhere)
    // are handled here when OpenCell is the default dialer: prefill the number
    // and surface the Phone tab rather than handing off to another dialer.
    LaunchedEffect(dialPadRequest) {
        val number = dialPadRequest ?: return@LaunchedEffect
        phoneViewModel.setDialedNumber(number)
        if (currentTab != NavRoute.Phone) {
            currentTab = NavRoute.Phone
            if (backStack.lastOrNull() != NavRoute.Phone) {
                backStack.add(NavRoute.Phone)
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        if (isWideScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    navTabItems.forEach { item ->
                        val selected = currentTab == item.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { onNavigateToTab(item.route) },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = { Text(item.title) }
                        )
                    }
                }

                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    entryProvider = { key ->
                        NavEntry(key) {
                            when (key) {
                                is NavRoute.Phone -> PhoneScreen(viewModel = phoneViewModel)
                                is NavRoute.Messages -> MessagesScreen(viewModel = messagesViewModel)
                                is NavRoute.Contacts -> ContactsScreen(
                                    viewModel = contactsViewModel,
                                    onCallClick = { num ->
                                        phoneViewModel.onDigitClick(num)
                                        onNavigateToTab(NavRoute.Phone)
                                    },
                                    onMessageClick = { _ ->
                                        messagesViewModel.openCompose()
                                        onNavigateToTab(NavRoute.Messages)
                                    }
                                )
                                is NavRoute.Recents -> RecentsScreen(
                                    viewModel = recentsViewModel,
                                    onRedialClick = { num ->
                                        phoneViewModel.onDigitClick(num)
                                        onNavigateToTab(NavRoute.Phone)
                                    }
                                )
                                is NavRoute.Settings -> SettingsScreen(viewModel = settingsViewModel)
                                is NavRoute.Developer -> DeveloperScreen(viewModel = developerViewModel)
                            }
                        }
                    }
                )
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        navTabItems.forEach { item ->
                            val selected = currentTab == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { onNavigateToTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title
                                    )
                                },
                                label = { Text(item.title) }
                            )
                        }
                    }
                }
            ) { padding ->
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    entryProvider = { key ->
                        NavEntry(key) {
                            when (key) {
                                is NavRoute.Phone -> PhoneScreen(viewModel = phoneViewModel)
                                is NavRoute.Messages -> MessagesScreen(viewModel = messagesViewModel)
                                is NavRoute.Contacts -> ContactsScreen(
                                    viewModel = contactsViewModel,
                                    onCallClick = { num ->
                                        phoneViewModel.onDigitClick(num)
                                        onNavigateToTab(NavRoute.Phone)
                                    },
                                    onMessageClick = { _ ->
                                        messagesViewModel.openCompose()
                                        onNavigateToTab(NavRoute.Messages)
                                    }
                                )
                                is NavRoute.Recents -> RecentsScreen(
                                    viewModel = recentsViewModel,
                                    onRedialClick = { num ->
                                        phoneViewModel.onDigitClick(num)
                                        onNavigateToTab(NavRoute.Phone)
                                    }
                                )
                                is NavRoute.Settings -> SettingsScreen(viewModel = settingsViewModel)
                                is NavRoute.Developer -> DeveloperScreen(viewModel = developerViewModel)
                            }
                        }
                    }
                )
            }
        }
    }
}
