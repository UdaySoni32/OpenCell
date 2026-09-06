package com.example.opencell.ui.phone

import com.example.opencell.data.repository.CallRepositoryImpl
import com.example.opencell.data.repository.FakeCallRecordDao
import com.example.opencell.domain.model.CallType
import com.example.opencell.telecom.CallEngine
import com.example.opencell.telecom.FakeTelecomAdapter
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

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: CallRepositoryImpl
    private lateinit var callEngine: CallEngine
    private lateinit var viewModel: PhoneViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = CallRepositoryImpl(FakeCallRecordDao())
        val fakeTelecomAdapter = FakeTelecomAdapter()
        callEngine = CallEngine(fakeTelecomAdapter, repository, externalScope = CoroutineScope(testDispatcher))
        viewModel = PhoneViewModel(repository, callEngine)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onDigitClick_appendsDigitToDialedNumber() {
        viewModel.onDigitClick("5")
        viewModel.onDigitClick("5")
        viewModel.onDigitClick("5")

        assertEquals("555", viewModel.dialedNumber.value)
    }

    @Test
    fun onBackspaceClick_removesLastDigit() {
        viewModel.onDigitClick("1")
        viewModel.onDigitClick("2")
        viewModel.onBackspaceClick()

        assertEquals("1", viewModel.dialedNumber.value)
    }

    @Test
    fun onClearClick_clearsAllDigits() {
        viewModel.onDigitClick("1")
        viewModel.onDigitClick("2")
        viewModel.onClearClick()

        assertEquals("", viewModel.dialedNumber.value)
    }

    @Test
    fun onCallClick_initiatesCallAndPersistsRecordWhenEnded() = runTest(testDispatcher) {
        viewModel.onDigitClick("9")
        viewModel.onDigitClick("1")
        viewModel.onDigitClick("1")
        viewModel.onCallClick()

        testScheduler.advanceUntilIdle()

        viewModel.hangupCall()
        testScheduler.advanceUntilIdle()

        val calls = repository.getAllCalls().first()
        assertEquals(1, calls.size)
        assertEquals("911", calls[0].phoneNumber)
        assertEquals(CallType.OUTGOING, calls[0].callType)
        assertEquals("", viewModel.dialedNumber.value)
    }
}
