package com.opencell.voice

import com.opencell.policy.ActionExecutor
import com.opencell.policy.ActionHandlers
import com.opencell.policy.ActionRequest
import com.opencell.policy.ActionResult
import com.opencell.policy.ActionType
import com.opencell.policy.Actor
import com.opencell.policy.CallGoal
import com.opencell.policy.FakePolicyDao
import com.opencell.policy.FakePrompter
import com.opencell.policy.PolicyMode
import com.opencell.policy.PolicySettings
import com.opencell.policy.Scope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceGoalApprovalTest {
    private val number = "+15550001111"
    private val device = Actor.Device(1, "laptop", Scope.DEFAULT_DEVICE)
    private val dao = FakePolicyDao()
    private var clock = 0L
    private val goals = OutgoingVoiceGoals { clock }
    private val calls = mutableListOf<Pair<String, String?>>()
    private var settings = PolicySettings()
    private val handlers = object : ActionHandlers {
        override suspend fun sendSms(to: String, body: String): Long? = null
        override fun controlCall(type: ActionType, callId: String) = false
        override fun placeCall(to: String, goal: String?) = goals.placeCall(goal) { token ->
            calls += to to token
            true
        }
    }
    private fun executor() = ActionExecutor(dao, { settings }, handlers, FakePrompter())
    private fun request(goal: String?) = ActionRequest(device, ActionType.PLACE_CALL, number, CallGoal.body(goal))

    @Test
    fun `pending and denied same number requests never arm goals or change an ordinary call`() = runTest {
        val executor = executor()
        assertTrue(executor.submit(request("first")) is ActionResult.Pending)
        assertTrue(executor.submit(request("second")) is ActionResult.Pending)
        assertTrue(calls.isEmpty())
        assertEquals("AI voice agent goal: first", dao.approval(1)!!.body)

        assertTrue(executor.submit(ActionRequest(Actor.User, ActionType.PLACE_CALL, number)) is ActionResult.Done)
        assertEquals(listOf(number to null), calls)
        assertNull(goals.take(calls.single().second))
        executor.decide(2, false, "you")
        assertEquals(1, calls.size)

        // A fresh executor reads only the persisted approval body, as after a process restart.
        clock = 24 * 60 * 60_000L // Approval can outlive the execution-to-connect TTL.
        executor().decide(1, true, "you")
        assertEquals("first", goals.take(calls.last().second))
        assertNull(executor.decide(2, true, "you"))
    }

    @Test
    fun `same number approved calls keep their own goals even when connected in reverse order`() = runTest {
        val executor = executor()
        executor.submit(request("first"))
        executor.submit(request("second"))
        executor.submit(request("denied"))
        executor.decide(2, true, "you")
        executor.decide(1, true, "you")
        executor.decide(3, false, "you")

        val secondToken = calls[0].second
        val firstToken = calls[1].second
        assertNotEquals(firstToken, secondToken)
        // A manual same-number call has no token; neither pending goal is consumed.
        assertNull(goals.take(null))
        assertEquals("first", goals.take(firstToken))
        assertEquals("second", goals.take(secondToken))
        assertNull(goals.take(firstToken))
    }

    @Test
    fun `immediate execution carries only its goal and policy denial never places a call`() = runTest {
        val executor = executor()
        settings = PolicySettings(mode = PolicyMode.FULL_AUTO)
        assertTrue(executor.submit(request("immediate")) is ActionResult.Done)
        settings = PolicySettings(killSwitch = true)
        assertTrue(executor.submit(request("blocked")) is ActionResult.Denied)
        assertEquals(1, calls.size)
        assertEquals("immediate", goals.take(calls.single().second))
    }

    @Test
    fun `old pending bodies decode at approval while plain call bodies stay ordinary`() = runTest {
        val executor = executor()
        // Deliberately construct the old wire/storage format without using the encoder.
        executor.submit(ActionRequest(device, ActionType.PLACE_CALL, number, "AI voice agent goal: book a table"))
        executor.submit(ActionRequest(device, ActionType.PLACE_CALL, number, "ordinary note"))
        executor.submit(request(null))
        executor.decide(1, true, "you")
        executor.decide(2, true, "you")
        executor.decide(3, true, "you")
        assertEquals("book a table", goals.take(calls[0].second))
        assertNull(calls[1].second)
        assertNull(calls[2].second)
        val literal = "AI voice agent goal: literal prefix\nsecond line"
        assertEquals(literal, CallGoal.fromBody(CallGoal.body(literal)))
    }

    @Test
    fun `failed or throwing placement removes only its own goal`() {
        var kept: String? = null
        var failed: String? = null
        var threw: String? = null
        assertTrue(goals.placeCall("keep") { kept = it; true })
        assertFalse(goals.placeCall("failed") { failed = it; false })
        assertTrue(runCatching {
            goals.placeCall("throws") { threw = it; error("cannot place") }
        }.isFailure)
        assertNull(goals.take(failed))
        assertNull(goals.take(threw))
        assertEquals("keep", goals.take(kept))
    }

    @Test
    fun `goals expire from execution time and disconnected calls cannot reclaim them`() {
        var now = 0L
        val goals = OutgoingVoiceGoals { now }
        var expired: String? = null
        var disconnected: String? = null
        goals.placeCall("expired") { expired = it; true }
        now = 10 * 60_000L
        assertNull(goals.take(expired))
        goals.placeCall("ended") { disconnected = it; true }
        goals.discard(disconnected)
        assertNull(goals.take(disconnected))
    }
}
