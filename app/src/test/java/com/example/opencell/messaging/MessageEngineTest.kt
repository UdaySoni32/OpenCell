package com.example.opencell.messaging

import android.app.PendingIntent
import com.example.opencell.data.repository.FakeMessageRecordDao
import com.example.opencell.data.repository.MessageRepositoryImpl
import com.example.opencell.domain.model.MessageStatus
import com.example.opencell.telecom.TestContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FakeSmsAdapter : SmsAdapter(TestContext()) {
    var isCapableValue = true
    var hasPermissionsValue = true
    var sendSmsResult = true

    override fun isSmsCapable(): Boolean = isCapableValue
    override fun hasSmsPermissions(): Boolean = hasPermissionsValue
    override fun sendSms(
        destination: String,
        text: String,
        sentIntent: PendingIntent?,
        deliveryIntent: PendingIntent?
    ): Boolean = sendSmsResult
}

@OptIn(ExperimentalCoroutinesApi::class)
class MessageEngineTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeMessageRecordDao
    private lateinit var repository: MessageRepositoryImpl
    private lateinit var fakeSmsAdapter: FakeSmsAdapter
    private lateinit var messageEngine: MessageEngine

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeMessageRecordDao()
        repository = MessageRepositoryImpl(fakeDao)
        fakeSmsAdapter = FakeSmsAdapter()
        messageEngine = MessageEngine(
            context = TestContext(),
            smsAdapter = fakeSmsAdapter,
            messageRepository = repository,
            externalScope = CoroutineScope(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun sendMessage_createsSendingRecordAndTransitionsToDeliveredInSimulation() = runTest(testDispatcher) {
        fakeSmsAdapter.isCapableValue = false
        messageEngine.sendMessage("555-0199", "Test cell towers", allowSimulationFallback = true)

        testScheduler.advanceUntilIdle()

        val messages = repository.getAllMessages().first()
        assertEquals(1, messages.size)
        assertEquals("555-0199", messages[0].address)
        assertEquals("Test cell towers", messages[0].body)
        assertEquals(MessageStatus.DELIVERED, messages[0].status)
    }
}
