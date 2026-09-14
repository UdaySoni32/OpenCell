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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.opencell.data.repository.CallRepository
import com.example.opencell.data.repository.MessageRepository
import com.example.opencell.messaging.MessageEngine
import com.example.opencell.navigation.NavRoute
import com.example.opencell.ui.contacts.ContactDetailsScreen
import com.example.opencell.ui.contacts.ContactDetailsViewModel
import com.example.opencell.ui.contacts.ContactsScreen
import com.example.opencell.ui.contacts.ContactsViewModel
import com.example.opencell.ui.developer.DeveloperScreen
import com.example.opencell.ui.developer.DeveloperViewModel
import com.example.opencell.ui.messages.ConversationScreen
import com.example.opencell.ui.messages.ConversationViewModel
import com.example.opencell.ui.messages.MessagesScreen
import com.example.opencell.ui.messages.MessagesViewModel
import com.example.opencell.ui.phone.PhoneScreenRoute
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
    NavTabItem(NavRoute.Messages, "SMS", Icons.AutoMirrored.Filled.Message),
    NavTabItem(NavRoute.Contacts, "Contacts", Icons.Default.Person),
    NavTabItem(NavRoute.Settings, "Settings", Icons.Default.Settings),
    NavTabItem(NavRoute.Developer, "Developer", Icons.Default.BugReport)
)

@Composable
fun AdaptiveNavigationShell(
    phoneViewModel: PhoneViewModel,
    messagesViewModel: MessagesViewModel,
    contactsViewModel: ContactsViewModel,
    recentsViewModel: RecentsViewModel,
    settingsViewModel: SettingsViewModel,
    developerViewModel: DeveloperViewModel,
    callRepository: CallRepository,
    messageRepository: MessageRepository,
    messageEngine: MessageEngine,
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

    LaunchedEffect(dialPadRequest) {
        val number = dialPadRequest ?: return@LaunchedEffect
        phoneViewModel.setDialedNumber(number)
        currentTab = NavRoute.Phone
        if (backStack.lastOrNull() != NavRoute.Phone) {
            backStack.add(NavRoute.Phone)
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
                                is NavRoute.Phone -> PhoneScreenRoute(
                                    viewModel = phoneViewModel,
                                    onBack = {
                                        if (backStack.size > 1) backStack.removeLastOrNull()
                                    },
                                    onMessageClick = { phone ->
                                        backStack.add(NavRoute.Conversation(phone))
                                    },
                                    onContactDetailsClick = { phone ->
                                        backStack.add(NavRoute.ContactDetails(phone))
                                    }
                                )
                                is NavRoute.Recents -> RecentsScreen(
                                    viewModel = recentsViewModel,
                                    phoneViewModel = phoneViewModel,
                                    onRedialClick = { num ->
                                        phoneViewModel.callNumber(num)
                                    },
                                    onMessageClick = { phone ->
                                        backStack.add(NavRoute.Conversation(phone))
                                    },
                                    onContactDetailsClick = { phone ->
                                        backStack.add(NavRoute.ContactDetails(phone))
                                    }
                                )
                                is NavRoute.Messages -> MessagesScreen(
                                    viewModel = messagesViewModel,
                                    onOpenConversation = { phone ->
                                        backStack.add(NavRoute.Conversation(phone))
                                    }
                                )
                                is NavRoute.Contacts -> ContactsScreen(
                                    viewModel = contactsViewModel,
                                    onCallClick = { num ->
                                        phoneViewModel.callNumber(num)
                                    },
                                    onMessageClick = { phone ->
                                        backStack.add(NavRoute.Conversation(phone))
                                    },
                                    onContactClick = { contactId ->
                                        backStack.add(NavRoute.ContactDetails(contactId))
                                    }
                                )
                                is NavRoute.ContactDetails -> {
                                    val vm: ContactDetailsViewModel = viewModel(
                                        key = "details_${key.contactId}",
                                        factory = ContactDetailsViewModel.Factory(
                                            contactId = key.contactId,
                                            callRepository = callRepository,
                                            messageRepository = messageRepository
                                        )
                                    )
                                    ContactDetailsScreen(
                                        viewModel = vm,
                                        onBack = {
                                            if (backStack.size > 1) backStack.removeLastOrNull()
                                        },
                                        onCallClick = { num ->
                                            phoneViewModel.callNumber(num)
                                        },
                                        onMessageClick = { phone ->
                                            backStack.add(NavRoute.Conversation(phone))
                                        }
                                    )
                                }
                                is NavRoute.Conversation -> {
                                    val vm: ConversationViewModel = viewModel(
                                        key = "conv_${key.phoneNumber}",
                                        factory = ConversationViewModel.Factory(
                                            phoneNumber = key.phoneNumber,
                                            repository = messageRepository,
                                            messageEngine = messageEngine
                                        )
                                    )
                                    ConversationScreen(
                                        viewModel = vm,
                                        onBack = {
                                            if (backStack.size > 1) backStack.removeLastOrNull()
                                        },
                                        onCallClick = { num ->
                                            phoneViewModel.callNumber(num)
                                        }
                                    )
                                }
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
                                is NavRoute.Phone -> PhoneScreenRoute(
                                    viewModel = phoneViewModel,
                                    onBack = {
                                        if (backStack.size > 1) backStack.removeLastOrNull()
                                    },
                                    onMessageClick = { phone ->
                                        backStack.add(NavRoute.Conversation(phone))
                                    },
                                    onContactDetailsClick = { phone ->
                                        backStack.add(NavRoute.ContactDetails(phone))
                                    }
                                )
                                is NavRoute.Recents -> RecentsScreen(
                                    viewModel = recentsViewModel,
                                    phoneViewModel = phoneViewModel,
                                    onRedialClick = { num ->
                                        phoneViewModel.callNumber(num)
                                    },
                                    onMessageClick = { phone ->
                                        backStack.add(NavRoute.Conversation(phone))
                                    },
                                    onContactDetailsClick = { phone ->
                                        backStack.add(NavRoute.ContactDetails(phone))
                                    }
                                )
                                is NavRoute.Messages -> MessagesScreen(
                                    viewModel = messagesViewModel,
                                    onOpenConversation = { phone ->
                                        backStack.add(NavRoute.Conversation(phone))
                                    }
                                )
                                is NavRoute.Contacts -> ContactsScreen(
                                    viewModel = contactsViewModel,
                                    onCallClick = { num ->
                                        phoneViewModel.callNumber(num)
                                    },
                                    onMessageClick = { phone ->
                                        backStack.add(NavRoute.Conversation(phone))
                                    },
                                    onContactClick = { contactId ->
                                        backStack.add(NavRoute.ContactDetails(contactId))
                                    }
                                )
                                is NavRoute.ContactDetails -> {
                                    val vm: ContactDetailsViewModel = viewModel(
                                        key = "details_${key.contactId}",
                                        factory = ContactDetailsViewModel.Factory(
                                            contactId = key.contactId,
                                            callRepository = callRepository,
                                            messageRepository = messageRepository
                                        )
                                    )
                                    ContactDetailsScreen(
                                        viewModel = vm,
                                        onBack = {
                                            if (backStack.size > 1) backStack.removeLastOrNull()
                                        },
                                        onCallClick = { num ->
                                            phoneViewModel.callNumber(num)
                                        },
                                        onMessageClick = { phone ->
                                            backStack.add(NavRoute.Conversation(phone))
                                        }
                                    )
                                }
                                is NavRoute.Conversation -> {
                                    val vm: ConversationViewModel = viewModel(
                                        key = "conv_${key.phoneNumber}",
                                        factory = ConversationViewModel.Factory(
                                            phoneNumber = key.phoneNumber,
                                            repository = messageRepository,
                                            messageEngine = messageEngine
                                        )
                                    )
                                    ConversationScreen(
                                        viewModel = vm,
                                        onBack = {
                                            if (backStack.size > 1) backStack.removeLastOrNull()
                                        },
                                        onCallClick = { num ->
                                            phoneViewModel.callNumber(num)
                                        }
                                    )
                                }
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
