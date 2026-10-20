package com.opencell.policy

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePolicyDao : PolicyDao {
    val audit = mutableListOf<AuditEntry>()
    val approvals = MutableStateFlow<List<ApprovalEntity>>(emptyList())

    override suspend fun insertAudit(entry: AuditEntry): Long {
        audit += entry.copy(id = audit.size + 1L)
        return audit.size.toLong()
    }
    override fun observeAudit(limit: Int): Flow<List<AuditEntry>> = MutableStateFlow(audit.toList())
    override suspend fun countOutboundSince(sinceMillis: Long) = audit.count {
        it.atMillis >= sinceMillis && it.outcome == Outcome.DONE && it.action in setOf(ActionType.SEND_SMS, ActionType.PLACE_CALL) && it.actor != "you"
    }
    override suspend fun insertApproval(approval: ApprovalEntity): Long {
        val id = approvals.value.size + 1L
        approvals.value = approvals.value + approval.copy(id = id)
        return id
    }
    override suspend fun updateApproval(approval: ApprovalEntity) {
        approvals.value = approvals.value.map { if (it.id == approval.id) approval else it }
    }
    override suspend fun approval(id: Long) = approvals.value.firstOrNull { it.id == id }
    override suspend fun recentApprovals(limit: Int) = approvals.value
    override fun observePending() = approvals.map { list -> list.filter { it.status == ApprovalStatus.PENDING } }
}

class FakeHandlers : ActionHandlers {
    val sent = mutableListOf<Pair<String, String>>()
    var smsFails = false
    var beforeSend: suspend () -> Unit = {}
    override suspend fun sendSms(to: String, body: String): Long? {
        beforeSend()
        return if (smsFails) null else { sent += to to body; sent.size.toLong() }
    }
    override fun placeCall(to: String, goal: String?) = true
    override fun controlCall(type: ActionType, callId: String) = callId == "call-0"
}

class FakePrompter : ApprovalPrompter {
    val shown = mutableListOf<Long>()
    val dismissed = mutableListOf<Long>()
    override fun show(approval: ApprovalEntity) { shown += approval.id }
    override fun dismiss(approvalId: Long) { dismissed += approvalId }
}

class ActionExecutorTest {
    private val dao = FakePolicyDao()
    private val handlers = FakeHandlers()
    private val prompter = FakePrompter()
    private var settings = PolicySettings()
    private val executor = ActionExecutor(dao, { settings }, handlers, prompter, now = { System.currentTimeMillis() })
    private val device = Actor.Device(1, "laptop", Scope.DEFAULT_DEVICE)

    @Test
    fun `ask mode queues approval and sends only after approve`() = runTest {
        val result = executor.submit(ActionRequest(device, ActionType.SEND_SMS, "+15550001111", "hello"))
        assertTrue(result is ActionResult.Pending)
        assertTrue(handlers.sent.isEmpty())
        assertEquals(listOf(1L), prompter.shown)

        val approved = executor.decide(1, approve = true, decidedBy = "you")!!
        assertEquals(ApprovalStatus.APPROVED, approved.status)
        assertEquals(listOf("+15550001111" to "hello"), handlers.sent)
        assertEquals(listOf(1L), prompter.dismissed)
        assertEquals(listOf(Outcome.PENDING, Outcome.DONE), dao.audit.map { it.outcome })
    }

    @Test
    fun `deny never sends and cannot be decided twice`() = runTest {
        executor.submit(ActionRequest(device, ActionType.SEND_SMS, "+15550001111", "hello"))
        assertEquals(ApprovalStatus.DENIED, executor.decide(1, approve = false, decidedBy = "you")!!.status)
        assertNull(executor.decide(1, approve = true, decidedBy = "you"))
        assertTrue(handlers.sent.isEmpty())
    }

    @Test
    fun `full auto sends immediately and counts toward the daily limit`() = runTest {
        settings = PolicySettings(mode = PolicyMode.FULL_AUTO, dailyLimit = 1)
        assertTrue(executor.submit(ActionRequest(device, ActionType.SEND_SMS, "1", "a")) is ActionResult.Done)
        assertTrue(executor.submit(ActionRequest(device, ActionType.SEND_SMS, "1", "b")) is ActionResult.Pending)
    }

    @Test
    fun `concurrent approvals execute a pending action at most once`() = runTest {
        executor.submit(ActionRequest(device, ActionType.SEND_SMS, "1", "hello"))
        handlers.beforeSend = { yield() }

        val decisions = List(10) {
            async { executor.decide(1, approve = true, decidedBy = "you") }
        }.awaitAll()

        assertEquals(1, decisions.count { it != null })
        assertEquals(listOf("1" to "hello"), handlers.sent)
        assertEquals(listOf(1L), prompter.dismissed)
        assertEquals(1, dao.audit.count { it.outcome == Outcome.DONE })
    }

    @Test
    fun `concurrent immediate submissions respect the daily limit`() = runTest {
        settings = PolicySettings(mode = PolicyMode.FULL_AUTO, dailyLimit = 1)
        handlers.beforeSend = { yield() }

        val results = List(10) { index ->
            async { executor.submit(ActionRequest(device, ActionType.SEND_SMS, "1", "message $index")) }
        }.awaitAll()

        assertEquals(1, results.count { it is ActionResult.Done })
        assertEquals(9, results.count { it is ActionResult.Pending })
        assertEquals(1, handlers.sent.size)
        assertEquals(1, dao.countOutboundSince(0))
    }

    @Test
    fun `submission waits for an in flight approval to count toward the limit`() = runTest {
        executor.submit(ActionRequest(device, ActionType.SEND_SMS, "1", "approved"))
        settings = PolicySettings(mode = PolicyMode.FULL_AUTO, dailyLimit = 1)
        handlers.beforeSend = { yield() }

        val approval = async { executor.decide(1, true, "you") }
        val submission = async { executor.submit(ActionRequest(device, ActionType.SEND_SMS, "1", "automatic")) }

        assertEquals(ApprovalStatus.APPROVED, approval.await()!!.status)
        assertTrue(submission.await() is ActionResult.Pending)
        assertEquals(listOf("1" to "approved"), handlers.sent)
    }

    @Test
    fun `approval cannot execute again if audit persistence fails after sending`() = runTest {
        val failingDao = object : PolicyDao by dao {
            override suspend fun insertAudit(entry: AuditEntry): Long {
                check(entry.outcome != Outcome.DONE) { "audit unavailable" }
                return dao.insertAudit(entry)
            }
        }
        val interruptedExecutor = ActionExecutor(failingDao, { settings }, handlers, prompter)
        interruptedExecutor.submit(ActionRequest(device, ActionType.SEND_SMS, "1", "hello"))

        assertTrue(runCatching { interruptedExecutor.decide(1, true, "you") }.isFailure)
        assertNull(interruptedExecutor.decide(1, true, "you"))
        assertEquals(listOf("1" to "hello"), handlers.sent)
        assertEquals(ApprovalStatus.APPROVED, dao.approval(1)!!.status)
    }

    @Test
    fun `remote approval checks the current kill switch and leaves local approval available`() = runTest {
        executor.submit(ActionRequest(device, ActionType.SEND_SMS, "1", "hello"))
        settings = PolicySettings(killSwitch = true, dailyLimit = 0)
        val approver = device.copy(scopes = setOf(Scope.APPROVE))

        assertNull(executor.decide(1, true, approver.label, decidingActor = approver))
        assertTrue(handlers.sent.isEmpty())
        assertEquals(ApprovalStatus.PENDING, dao.approval(1)!!.status)
        assertTrue(prompter.dismissed.isEmpty())
        assertEquals(Outcome.DENIED, dao.audit.last().outcome)
        assertEquals("kill switch is on", dao.audit.last().detail)

        assertEquals(ApprovalStatus.APPROVED, executor.decide(1, true, "you", decidingActor = Actor.User)!!.status)
        assertEquals(listOf("1" to "hello"), handlers.sent)
    }

    @Test
    fun `remote explicit approval bypasses automatic asking and daily limit`() = runTest {
        executor.submit(ActionRequest(device, ActionType.SEND_SMS, "1", "hello"))
        settings = PolicySettings(mode = PolicyMode.ASK, dailyLimit = 0)
        val approver = device.copy(scopes = setOf(Scope.APPROVE))

        assertEquals(ApprovalStatus.APPROVED, executor.decide(1, true, approver.label, decidingActor = approver)!!.status)
        assertEquals(listOf("1" to "hello"), handlers.sent)
    }

    @Test
    fun `remote denial remains available with kill switch on`() = runTest {
        executor.submit(ActionRequest(device, ActionType.SEND_SMS, "1", "hello"))
        settings = PolicySettings(killSwitch = true)

        assertEquals(ApprovalStatus.DENIED, executor.decide(1, false, device.label, decidingActor = device)!!.status)
        assertTrue(handlers.sent.isEmpty())
    }

    @Test
    fun `failures and denials are audited`() = runTest {
        settings = PolicySettings(mode = PolicyMode.FULL_AUTO)
        handlers.smsFails = true
        assertTrue(executor.submit(ActionRequest(device, ActionType.SEND_SMS, "1", "a")) is ActionResult.Failed)
        settings = PolicySettings(killSwitch = true)
        assertTrue(executor.submit(ActionRequest(device, ActionType.PLACE_CALL, "1")) is ActionResult.Denied)
        assertEquals(listOf(Outcome.FAILED, Outcome.DENIED), dao.audit.map { it.outcome })
    }

    @Test
    fun `call control reports unknown calls as failed`() = runTest {
        assertTrue(executor.submit(ActionRequest(device, ActionType.HANGUP_CALL, "call-0")) is ActionResult.Done)
        assertTrue(executor.submit(ActionRequest(device, ActionType.HANGUP_CALL, "call-9")) is ActionResult.Failed)
    }
}
