package com.opencell.ui.contacts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencell.AppContainer
import com.opencell.contacts.Contact
import com.opencell.ui.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ContactsViewModel(container: AppContainer) : ViewModel() {
    val contacts: StateFlow<List<Contact>> = container.contactsRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

fun filterContacts(contacts: List<Contact>, query: String): List<Contact> {
    val q = query.trim()
    if (q.isEmpty()) return contacts
    val digits = q.filter(Char::isDigit)
    return contacts.filter { c ->
        c.name.contains(q, ignoreCase = true) || (digits.isNotEmpty() && c.number.filter(Char::isDigit).contains(digits))
    }
}

@Composable
fun ContactsScreen(onCall: (String) -> Unit, onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
    val vm = appViewModel { ContactsViewModel(it) }
    val all by vm.contacts.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(all, query) { filterContacts(all, query) }
    Column(modifier.fillMaxSize()) {
        Text("Contacts", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Search name or number") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyColumn {
            items(shown, key = { "${it.id}-${it.number}" }) { c ->
                ListItem(
                    headlineContent = { Text(c.name) },
                    supportingContent = { Text(c.number) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onMessage(c.number) }) { Icon(Icons.AutoMirrored.Filled.Message, "Message") }
                            IconButton(onClick = { onCall(c.number) }) { Icon(Icons.Filled.Call, "Call") }
                        }
                    },
                )
            }
        }
    }
}
