package com.example.opencell.ui.messages

import com.example.opencell.data.repository.FakeMessageRecordDao
import com.example.opencell.data.repository.MessageRepositoryImpl
import com.example.opencell.domain.model.MessageRecord
import com.example.opencell.messaging.FakeSmsAdapter
import com.example.opencell.messaging.MessageEngine
import com.example.opencell.telecom.TestContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: MessageRepositoryImpl
    private lateinit var messageEngine: MessageEngine
    private lateinit var viewModel: MessagesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = MessageRepositoryImpl(FakeMessageRecordDao())
        val context = TestContext()
        val smsAdapter = FakeSmsAdapter()
        messageEngine = MessageEngine(context, smsAdapter, repository, externalScope = CoroutineScope(testDispatcher))
        viewModel = MessagesViewModel(repository, messageEngine)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun openAndCloseCompose_updatesIsComposeOpen() {
        assertFalse(viewModel.isComposeOpen.value)

        viewModel.openCompose()
        assertTrue(viewModel.isComposeOpen.value)

        viewModel.closeCompose()
        assertFalse(viewModel.isComposeOpen.value)
    }

    @Test
    fun sendMessage_insertsRecordAndClosesCompose() = runTest(testDispatcher) {
        viewModel.openCompose()
        viewModel.sendMessage("555-0100", "Tower maintenance complete")

        testScheduler.advanceUntilIdle()

        val messages = repository.getAllMessages().first()
        assertEquals(1, messages.size)
        assertEquals("555-0100", messages[0].address)
        assertEquals("Tower maintenance complete", messages[0].body)
        assertFalse(viewModel.isComposeOpen.value)
    }

    @Test
    fun searchQuery_filtersMessages() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.messages.collect() }

        repository.insertMessage(MessageRecord(address = "Alice", body = "LTE signal"))
        repository.insertMessage(MessageRecord(address = "Bob", body = "5G NR speed test"))

        testScheduler.advanceUntilIdle()

        viewModel.onSearchQueryChange("5G")
        testScheduler.advanceUntilIdle()

        val filtered = viewModel.messages.value
        assertEquals(1, filtered.size)
        assertEquals("Bob", filtered[0].address)
    }
}
