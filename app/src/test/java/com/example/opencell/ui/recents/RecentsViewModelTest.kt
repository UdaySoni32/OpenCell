package com.example.opencell.ui.recents

import com.example.opencell.data.repository.CallRepositoryImpl
import com.example.opencell.data.repository.FakeCallRecordDao
import com.example.opencell.domain.model.CallRecord
import com.example.opencell.domain.model.CallType
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecentsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: CallRepositoryImpl
    private lateinit var viewModel: RecentsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = CallRepositoryImpl(FakeCallRecordDao())
        viewModel = RecentsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setFilter_filtersCallsByType() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.calls.collect() }

        repository.insertCall(CallRecord(phoneNumber = "111", callType = CallType.INCOMING))
        repository.insertCall(CallRecord(phoneNumber = "222", callType = CallType.MISSED))

        testScheduler.advanceUntilIdle()

        assertEquals(2, viewModel.calls.value.size)

        viewModel.setFilter(CallType.MISSED)
        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.calls.value.size)
        assertEquals("222", viewModel.calls.value[0].phoneNumber)
    }

    @Test
    fun clearAllCalls_clearsHistory() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.calls.collect() }

        repository.insertCall(CallRecord(phoneNumber = "111", callType = CallType.INCOMING))
        testScheduler.advanceUntilIdle()

        viewModel.clearAllCalls()
        testScheduler.advanceUntilIdle()

        val calls = repository.getAllCalls().first()
        assertEquals(0, calls.size)
    }
}
