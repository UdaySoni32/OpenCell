package com.example.opencell.ui.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.opencell.domain.model.Contact
import com.example.opencell.ui.theme.OpenCellTheme
import com.example.opencell.ui.theme.successColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    onCallClick: (String) -> Unit = {},
    onMessageClick: (String) -> Unit = {},
    onContactClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val groupedContacts by viewModel.groupedContacts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isAddContactOpen by viewModel.isAddContactOpen.collectAsStateWithLifecycle()

    ContactsScreenContent(
        contacts = contacts,
        groupedContacts = groupedContacts,
        searchQuery = searchQuery,
        isAddContactOpen = isAddContactOpen,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onOpenAddContact = viewModel::openAddContact,
        onCloseAddContact = viewModel::closeAddContact,
        onAddContact = viewModel::addContact,
        onDeleteContact = viewModel::deleteContact,
        onCallClick = onCallClick,
        onMessageClick = onMessageClick,
        onContactClick = onContactClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreenContent(
    contacts: List<Contact>,
    groupedContacts: Map<String, List<Contact>> = emptyMap(),
    searchQuery: String,
    isAddContactOpen: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onOpenAddContact: () -> Unit,
    onCloseAddContact: () -> Unit,
    onAddContact: (String, String, String?) -> Unit,
    onDeleteContact: (Contact) -> Unit,
    onCallClick: (String) -> Unit,
    onMessageClick: (String) -> Unit,
    onContactClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Contacts")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenAddContact,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Contact") },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                placeholder = { Text("Search contacts by name, number, or carrier...") },
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

            Spacer(modifier = Modifier.height(4.dp))

            if (contacts.isEmpty()) {
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
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No Contacts" else "No matching contacts",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val displayGroupedContacts = remember(contacts, groupedContacts) {
                    groupedContacts.ifEmpty {
                        contacts.sortedWith(ContactsViewModel.contactComparator)
                            .groupBy { ContactsViewModel.getSectionHeader(it) }
                    }
                }

                val listState = rememberLazyListState()
                val coroutineScope = rememberCoroutineScope()

                val (sectionIndices, alphabetLetters) = remember(displayGroupedContacts) {
                    val indices = mutableMapOf<String, Int>()
                    var currentIndex = 0
                    displayGroupedContacts.forEach { (sectionKey, sectionContacts) ->
                        indices[sectionKey] = currentIndex
                        currentIndex += 1 + sectionContacts.size
                    }
                    val letters = ('A'..'Z').map { it.toString() } + "#"
                    indices to letters
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        displayGroupedContacts.forEach { (sectionKey, sectionContacts) ->
                            item(key = "header_$sectionKey") {
                                SectionHeader(letter = sectionKey)
                            }
                            items(
                                items = sectionContacts,
                                key = { it.id }
                            ) { contact ->
                                val onContactClickLambda = remember(contact.id, onContactClick) { { onContactClick(contact.id) } }
                                val onCallClickLambda = remember(contact.phoneNumber, onCallClick) { { onCallClick(contact.phoneNumber) } }
                                val onMessageClickLambda = remember(contact.phoneNumber, onMessageClick) { { onMessageClick(contact.phoneNumber) } }
                                val onDeleteContactLambda = remember(contact, onDeleteContact) { { onDeleteContact(contact) } }

                                ContactItemCard(
                                    contact = contact,
                                    onContactClick = onContactClickLambda,
                                    onCall = onCallClickLambda,
                                    onMessage = onMessageClickLambda,
                                    onDelete = onDeleteContactLambda
                                )
                            }
                        }
                    }

                    AlphabetFastScroller(
                        alphabet = alphabetLetters,
                        sectionIndices = sectionIndices,
                        onLetterSelected = { letter ->
                            sectionIndices[letter]?.let { targetIndex ->
                                coroutineScope.launch {
                                    listState.scrollToItem(targetIndex)
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }
    }

    if (isAddContactOpen) {
        AddContactDialog(
            onDismiss = onCloseAddContact,
            onAdd = onAddContact
        )
    }
}

@Composable
fun AlphabetFastScroller(
    alphabet: List<String>,
    sectionIndices: Map<String, Int>,
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedLetter by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = modifier.width(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            alphabet.forEach { letter ->
                val isPresent = sectionIndices.containsKey(letter)
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = if (isPresent) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isPresent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    modifier = Modifier
                        .clickable(enabled = isPresent) {
                            selectedLetter = letter
                            onLetterSelected(letter)
                        }
                        .padding(vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    letter: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ContactItemCard(
    contact: Contact,
    onContactClick: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onContactClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val avatarLetter = contact.name.ifBlank { contact.phoneNumber }.trim().take(1).uppercase()
                    Text(
                        text = avatarLetter,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = contact.name.ifBlank { contact.phoneNumber },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (contact.name.isNotBlank()) {
                        Text(
                            text = contact.phoneNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!contact.carrierLabel.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = contact.carrierLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = onCall, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call",
                        tint = successColor(),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onMessage, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = "Message",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddContactDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var carrier by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Add New Contact")
            }
        },
        shape = RoundedCornerShape(28.dp),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = carrier,
                    onValueChange = { carrier = it },
                    label = { Text("Carrier Label (Optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, phone, carrier) },
                enabled = name.isNotBlank() && phone.isNotBlank()
            ) {
                Text("Save")
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
fun ContactsScreenPreview() {
    OpenCellTheme {
        ContactsScreenContent(
            contacts = listOf(
                Contact("1", "Alice Smith", "+1 (555) 012-3456", carrierLabel = "T-Mobile LTE"),
                Contact("2", "Emergency Ops", "911", carrierLabel = "Priority Network"),
                Contact("3", "123 Direct Line", "+1 (800) 555-0100")
            ),
            searchQuery = "",
            isAddContactOpen = false,
            onSearchQueryChange = {},
            onOpenAddContact = {},
            onCloseAddContact = {},
            onAddContact = { _, _, _ -> },
            onDeleteContact = {},
            onCallClick = {},
            onMessageClick = {},
            onContactClick = {}
        )
    }
}
