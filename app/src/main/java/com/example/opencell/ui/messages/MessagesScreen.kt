package com.example.opencell.ui.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.opencell.domain.model.Contact
import com.example.opencell.domain.model.MessageRecord
import com.example.opencell.domain.model.MessageStatus
import com.example.opencell.ui.contacts.ContactStore
import com.example.opencell.ui.theme.OpenCellTheme
import com.example.opencell.ui.theme.successColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel,
    onOpenConversation: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isComposeOpen by viewModel.isComposeOpen.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isSmsCapable by viewModel.isSmsCapable.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    MessagesScreenContent(
        messages = messages,
        searchQuery = searchQuery,
        isComposeOpen = isComposeOpen,
        isSmsCapable = isSmsCapable,
        snackbarHostState = snackbarHostState,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onOpenCompose = viewModel::openCompose,
        onCloseCompose = viewModel::closeCompose,
        onOpenConversation = { phone ->
            viewModel.closeCompose()
            onOpenConversation(phone)
        },
        onSendMessage = viewModel::sendMessage,
        onDeleteMessage = viewModel::deleteMessage,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreenContent(
    messages: List<MessageRecord>,
    searchQuery: String,
    isComposeOpen: Boolean,
    isSmsCapable: Boolean,
    snackbarHostState: SnackbarHostState,
    onSearchQueryChange: (String) -> Unit,
    onOpenCompose: () -> Unit,
    onCloseCompose: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onSendMessage: (String, String) -> Unit,
    onDeleteMessage: (MessageRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Message,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("SMS")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenCompose,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Start chat") },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (!isSmsCapable) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "Cellular Modem Offline / SMS Simulation Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp)
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("Search SMS conversations") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Message,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No Conversations Yet" else "No matching conversations",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Tap Start chat to compose an SMS",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { "conv_${it.id}" }) { message ->
                        val onClickLambda = remember(message.address, onOpenConversation) { { onOpenConversation(message.address) } }
                        val onDeleteLambda = remember(message, onDeleteMessage) { { onDeleteMessage(message) } }
                        ConversationItemCard(
                            message = message,
                            onClick = onClickLambda,
                            onDelete = onDeleteLambda
                        )
                    }
                }
            }
        }
    }

    if (isComposeOpen) {
        RedesignedComposeSmsDialog(
            onDismiss = onCloseCompose,
            onSelectRecipient = onOpenConversation,
            onSendDirectMessage = onSendMessage
        )
    }
}

@Composable
fun ConversationItemCard(
    message: MessageRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val timeStr = remember(message.timestamp) { dateFormat.format(Date(message.timestamp)) }
    val isUnread = !message.isRead

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnread) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = if (message.isIncoming) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = (message.contactName ?: message.address).take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (message.isIncoming) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = message.contactName ?: message.address,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isUnread) FontWeight.ExtraBold else FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isUnread) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "UNREAD",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
                        )
                        if (!message.isIncoming) {
                            Spacer(modifier = Modifier.width(6.dp))
                            StatusIcon(status = message.status)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Conversation",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun StatusIcon(status: MessageStatus) {
    val (icon, color, description) = when (status) {
        MessageStatus.SENDING -> Triple(Icons.Default.HourglassEmpty, MaterialTheme.colorScheme.outline, "Sending")
        MessageStatus.SENT -> Triple(Icons.Default.Check, MaterialTheme.colorScheme.primary, "Sent")
        MessageStatus.DELIVERED -> Triple(Icons.Default.CheckCircle, successColor(), "Delivered")
        MessageStatus.FAILED -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, "Failed")
        MessageStatus.RECEIVED -> Triple(Icons.Default.Check, MaterialTheme.colorScheme.secondary, "Received")
    }

    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = color,
        modifier = Modifier.size(16.dp)
    )
}

@Composable
fun RedesignedComposeSmsDialog(
    onDismiss: () -> Unit,
    onSelectRecipient: (String) -> Unit,
    onSendDirectMessage: (String, String) -> Unit
) {
    val contacts by ContactStore.contacts.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var selectedPhone by remember { mutableStateOf<String?>(null) }
    var messageBody by remember { mutableStateOf("") }

    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) contacts
        else contacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.phoneNumber.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("New Message")
            }
        },
        shape = RoundedCornerShape(28.dp),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Recipient Picker
                if (selectedPhone != null || selectedContact != null) {
                    val displayName = selectedContact?.name ?: selectedPhone ?: ""
                    val phone = selectedContact?.phoneNumber ?: selectedPhone ?: ""

                    Text(
                        text = "To:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    AssistChip(
                        onClick = {},
                        label = { Text("$displayName ($phone)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    selectedContact = null
                                    selectedPhone = null
                                },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear recipient"
                                )
                            }
                        }
                    )
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Type contact name or phone number") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (searchQuery.isNotBlank() && searchQuery.any { it.isDigit() }) {
                        TextButton(
                            onClick = {
                                selectedPhone = searchQuery.trim()
                                onSelectRecipient(searchQuery.trim())
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Use raw number: $searchQuery")
                        }
                    }

                    // Autocomplete live dropdown
                    Text(
                        text = "Matching Contacts:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredContacts, key = { it.id }) { contact ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedContact = contact
                                        selectedPhone = contact.phoneNumber
                                        onSelectRecipient(contact.phoneNumber)
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(32.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.tertiaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = contact.name.take(1).uppercase(),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.name.ifBlank { contact.phoneNumber },
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = contact.phoneNumber,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedPhone != null) {
                    OutlinedTextField(
                        value = messageBody,
                        onValueChange = { messageBody = it },
                        label = { Text("Message Body (Optional)") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            val targetPhone = selectedPhone ?: selectedContact?.phoneNumber
            TextButton(
                onClick = {
                    if (targetPhone != null) {
                        if (messageBody.isNotBlank()) {
                            onSendDirectMessage(targetPhone, messageBody)
                        } else {
                            onSelectRecipient(targetPhone)
                        }
                    }
                },
                enabled = targetPhone != null
            ) {
                Text("Start Chat")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun MessagesScreenPreview() {
    OpenCellTheme {
        MessagesScreenContent(
            messages = listOf(
                MessageRecord(
                    id = 1,
                    address = "+1 (555) 012-3456",
                    contactName = "Carrier Alert",
                    body = "Welcome to OpenCell network test mode. Signal quality is optimal.",
                    timestamp = System.currentTimeMillis() - 3600000,
                    isIncoming = true,
                    isRead = false,
                    status = MessageStatus.RECEIVED
                ),
                MessageRecord(
                    id = 2,
                    address = "555-0199",
                    contactName = "Field Tech",
                    body = "Cell tower scan complete for sector 4.",
                    timestamp = System.currentTimeMillis() - 7200000,
                    isIncoming = false,
                    isRead = true,
                    status = MessageStatus.DELIVERED
                )
            ),
            searchQuery = "",
            isComposeOpen = false,
            isSmsCapable = true,
            snackbarHostState = remember { SnackbarHostState() },
            onSearchQueryChange = {},
            onOpenCompose = {},
            onCloseCompose = {},
            onOpenConversation = {},
            onSendMessage = { _, _ -> },
            onDeleteMessage = {}
        )
    }
}
