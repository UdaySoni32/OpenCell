package com.example.opencell.ui.contacts

import com.example.opencell.domain.model.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeContactList = MutableStateFlow<List<Contact>>(emptyList())
    private lateinit var viewModel: ContactsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeContactList.value = emptyList()
        viewModel = ContactsViewModel(fakeContactList)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun contacts_sortedAlphabeticallyBySectionAndName() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.contacts.collect() }

        fakeContactList.value = listOf(
            Contact("1", "Charlie", "111"),
            Contact("2", "Alice", "222"),
            Contact("3", "bob", "333"),
            Contact("4", "adam", "444"),
            Contact("5", "123 Hotline", "555"),
            Contact("6", "+1 (800) 555-0100", "666"),
            Contact("7", "", "+19998887777")
        )

        testScheduler.advanceUntilIdle()

        val sorted = viewModel.contacts.value
        assertEquals(7, sorted.size)
        assertEquals("adam", sorted[0].name)
        assertEquals("Alice", sorted[1].name)
        assertEquals("bob", sorted[2].name)
        assertEquals("Charlie", sorted[3].name)
        assertEquals("+1 (800) 555-0100", sorted[4].name)
        assertEquals("", sorted[5].name) // Unsaved contact "+19998887777"
        assertEquals("123 Hotline", sorted[6].name)
    }

    @Test
    fun groupedContacts_groupsByAThroughZAndHashAtBottom() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.contacts.collect() }
        backgroundScope.launch { viewModel.groupedContacts.collect() }

        fakeContactList.value = listOf(
            Contact("1", "Charlie", "111"),
            Contact("2", "Alice", "222"),
            Contact("3", "bob", "333"),
            Contact("4", "adam", "444"),
            Contact("5", "123 Hotline", "555"),
            Contact("6", "+1 (800) 555-0100", "666"),
            Contact("7", "", "+19998887777")
        )

        testScheduler.advanceUntilIdle()

        val grouped = viewModel.groupedContacts.value
        val keys = grouped.keys.toList()

        assertEquals(listOf("A", "B", "C", "#"), keys)

        assertEquals(listOf("adam", "Alice"), grouped["A"]?.map { it.name })
        assertEquals(listOf("bob"), grouped["B"]?.map { it.name })
        assertEquals(listOf("Charlie"), grouped["C"]?.map { it.name })
        assertEquals(listOf("+1 (800) 555-0100", "", "123 Hotline"), grouped["#"]?.map { it.name })
    }

    @Test
    fun searchQuery_filtersAndMaintainsSortingAndSectioning() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.contacts.collect() }
        backgroundScope.launch { viewModel.groupedContacts.collect() }

        fakeContactList.value = listOf(
            Contact("1", "Alice", "111"),
            Contact("2", "Bob", "222"),
            Contact("3", "123 Hotline", "333")
        )

        testScheduler.advanceUntilIdle()

        viewModel.onSearchQueryChange("123")
        testScheduler.advanceUntilIdle()

        val filtered = viewModel.contacts.value
        assertEquals(1, filtered.size)
        assertEquals("123 Hotline", filtered[0].name)

        val grouped = viewModel.groupedContacts.value
        assertEquals(setOf("#"), grouped.keys)
    }

    @Test
    fun sectionHeaderDetermination_handlesSpecialCharactersAndNumbersAndBlankNames() {
        val letterA = Contact("1", "Alice", "111")
        val letterB = Contact("2", "bob", "222")
        val digit = Contact("3", "911 Emergency", "911")
        val symbol = Contact("4", "+1 (800) 555-0100", "800")
        val blankName = Contact("5", "", "+15551234")
        val emptyNameAndPhone = Contact("6", "", "")

        assertEquals("A", ContactsViewModel.getSectionHeader(letterA))
        assertEquals("B", ContactsViewModel.getSectionHeader(letterB))
        assertEquals("#", ContactsViewModel.getSectionHeader(digit))
        assertEquals("#", ContactsViewModel.getSectionHeader(symbol))
        assertEquals("#", ContactsViewModel.getSectionHeader(blankName))
        assertEquals("#", ContactsViewModel.getSectionHeader(emptyNameAndPhone))
    }

    @Test
    fun contacts_deduplicatesSamePhoneNumberAndSameNameWithWhitespace() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.contacts.collect() }

        fakeContactList.value = listOf(
            Contact("1", "Alice Smith", "+1 (555) 012-3456"),
            Contact("2", "Alice Smith ", "555-012-3456"),
            Contact("3", "Bob Johnson", "+15559876543"),
            Contact("4", "  Bob Johnson  ", ""),
            Contact("5", "Bob Johnson", "")
        )

        testScheduler.advanceUntilIdle()

        val list = viewModel.contacts.value
        assertEquals(3, list.size)
        assertEquals("Alice Smith", list[0].name)
        assertEquals("+1 (555) 012-3456", list[0].phoneNumber)
        assertEquals("Bob Johnson", list[1].name)
        assertEquals("+15559876543", list[1].phoneNumber)
        assertEquals("Bob Johnson", list[2].name)
        assertEquals("", list[2].phoneNumber)
    }
}
