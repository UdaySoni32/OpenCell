package io.opencell.platform.telecom

import android.content.Context
import io.opencell.core.database.dao.AuditLogDao
import io.opencell.core.database.dao.CallDao
import io.opencell.core.database.entity.CallEntity
import io.opencell.core.model.CallState
import io.opencell.platform.events.EventEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallEngineTest {

    private lateinit var context: Context
    private lateinit var callDao: CallDao
    private lateinit var auditLogDao: AuditLogDao
    private lateinit var eventEngine: EventEngine
    private lateinit var callEngine: CallEngine

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        callDao = mockk(relaxed = true)
        auditLogDao = mockk(relaxed = true)
        eventEngine = mockk(relaxed = true)
        callEngine = CallEngine(context, callDao, auditLogDao, eventEngine)
    }

    @Test
    fun `makeCall creates call record and emits event`() = runTest {
        val phoneNumber = "+1234567890"
        val deviceId = "dev_1"

        val result = callEngine.makeCall(phoneNumber, deviceId = deviceId)

        assertTrue(result.isSuccess)
        val call = result.getOrNull()
        assertNotNull(call)
        assertEquals(phoneNumber, call?.to)
        assertEquals(CallState.DIALING, call?.state)

        coVerify { callDao.upsertCall(any()) }
        coVerify { eventEngine.emit("call.created", deviceId, any()) }
    }

    @Test
    fun `onIncomingCall updates state and current call`() = runTest {
        val callId = "call_123"
        val phoneNumber = "+1987654321"
        val deviceId = "dev_1"

        callEngine.onIncomingCall(callId, phoneNumber, "Test Caller", deviceId)

        assertEquals(callId, callEngine.currentCall.value?.id)
        assertEquals(CallState.RINGING, callEngine.currentCall.value?.state)
        
        coVerify { callDao.upsertCall(any()) }
    }

    @Test
    fun `answerCall fails if call is not ringing`() = runTest {
        val callId = "call_123"
        coEvery { callDao.getCall(callId) } returns mockk<CallEntity> {
            every { state } returns CallState.ACTIVE.name
        }

        val result = callEngine.answerCall(callId)

        assertTrue(result.isFailure)
        assertEquals("Call is not ringing", result.exceptionOrNull()?.message)
    }

    @Test
    fun `hangupCall calculates duration if answered`() = runTest {
        val callId = "call_123"
        val now = System.currentTimeMillis()
        val answeredAt = now - 5000 // 5 seconds ago
        
        coEvery { callDao.getCall(callId) } returns mockk<CallEntity> {
            every { state } returns CallState.ACTIVE.name
            every { deviceId } returns "dev_1"
            every { answeredAt } returns answeredAt
        }

        callEngine.hangupCall(callId)

        coVerify { 
            callDao.updateCallState(
                id = callId, 
                state = CallState.ENDED.name, 
                durationMs = any(), 
                endedAt = any()
            ) 
        }
    }
    
    private fun assertNotNull(value: Any?) {
        assertTrue("Value should not be null", value != null)
    }
}
