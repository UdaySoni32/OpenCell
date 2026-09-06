package com.example.opencell.telecom

import com.example.opencell.data.repository.CallRepositoryImpl
import com.example.opencell.data.repository.FakeCallRecordDao
import com.example.opencell.domain.model.CallState
import com.example.opencell.domain.model.CallType
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallEngineTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeCallRecordDao
    private lateinit var repository: CallRepositoryImpl
    private lateinit var fakeTelecomAdapter: FakeTelecomAdapter
    private lateinit var callEngine: CallEngine

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeCallRecordDao()
        repository = CallRepositoryImpl(fakeDao)
        fakeTelecomAdapter = FakeTelecomAdapter()
        callEngine = CallEngine(fakeTelecomAdapter, repository, externalScope = CoroutineScope(testDispatcher))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initiateCall_startsCallSession() = runTest(testDispatcher) {
        callEngine.initiateCall("555-0199")

        val session = callEngine.activeCallSession.value
        assertNotNull(session)
        assertEquals("555-0199", session?.phoneNumber)
        assertEquals(CallState.DIALING, session?.state)
    }

    @Test
    fun hangupCall_endsSessionAndPersistsCallRecord() = runTest(testDispatcher) {
        callEngine.initiateCall("555-0199")
        testScheduler.advanceUntilIdle()

        callEngine.hangupCall()
        testScheduler.advanceUntilIdle()

        assertNull(callEngine.activeCallSession.value)

        val records = repository.getAllCalls().first()
        assertEquals(1, records.size)
        assertEquals("555-0199", records[0].phoneNumber)
        assertEquals(CallType.OUTGOING, records[0].callType)
    }

    @Test
    fun toggleMuteAndSpeaker_updatesSessionFlags() = runTest(testDispatcher) {
        callEngine.initiateCall("555-0199")
        testScheduler.advanceUntilIdle()

        callEngine.toggleMute()
        assertTrue(callEngine.activeCallSession.value?.isMuted == true)

        callEngine.toggleSpeaker()
        assertTrue(callEngine.activeCallSession.value?.isSpeakerOn == true)
    }

    @Test
    fun toggleHold_updatesSessionHoldState() = runTest(testDispatcher) {
        callEngine.initiateCall("555-0199")
        testScheduler.advanceUntilIdle()

        callEngine.toggleHold()
        assertTrue(callEngine.activeCallSession.value?.isOnHold == true)
        assertEquals(CallState.HELD, callEngine.activeCallSession.value?.state)

        callEngine.toggleHold()
        assertTrue(callEngine.activeCallSession.value?.isOnHold == false)
        assertEquals(CallState.ACTIVE, callEngine.activeCallSession.value?.state)

        // End the call so the engine's duration-timer coroutine stops; leaving it
        // running would keep the test scheduler busy forever.
        callEngine.hangupCall()
        testScheduler.advanceUntilIdle()
        assertNull(callEngine.activeCallSession.value)
    }
}
