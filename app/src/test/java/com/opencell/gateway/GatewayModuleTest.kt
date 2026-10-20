package com.opencell.gateway

import com.opencell.policy.ActionResult
import com.opencell.policy.Actor
import com.opencell.policy.Scope
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeBackend : GatewayBackend {
    override val version = "test"
    val full = Actor.Device(1, "laptop", Scope.DEFAULT_DEVICE)
    val readOnly = Actor.Device(2, "pi", setOf(Scope.READ))
    var smsResult: ActionResult = ActionResult.Pending(7)
    val sent = mutableListOf<Pair<String, String>>()
    var revoked = false
    var loseReadScope = false
    var eventFlow: Flow<EventDto> = emptyFlow()

    override suspend fun authenticate(token: String) = when (token) {
        "full" -> if (revoked) null else if (loseReadScope) full.copy(scopes = emptySet()) else full
        "ro" -> readOnly
        else -> null
    }
    override suspend fun pair(code: String, name: String) =
        if (code == "123456") PairResponse("oc_new", 3, listOf("read")) else null
    override suspend fun listMessages(limit: Int) = listOf(MessageDto("SM1", "+1555", "me", "hi", "received", "inbound", 1))
    override suspend fun message(id: Long) = if (id == 1L) listMessages(1).first() else null
    override suspend fun listCalls(limit: Int) = emptyList<CallLogDto>()
    override fun activeCalls() = emptyList<ActiveCallDto>()
    override suspend fun listApprovals() = emptyList<ApprovalDto>()
    override suspend fun submitSms(device: Actor.Device, to: String, body: String): ActionResult {
        sent += to to body
        return smsResult
    }
    var lastGoal: String? = null
    override suspend fun submitCall(device: Actor.Device, to: String, agentGoal: String?): ActionResult {
        lastGoal = agentGoal
        return ActionResult.Done(null)
    }
    override suspend fun submitCallAction(device: Actor.Device, callSid: String, action: String) = ActionResult.Failed("no such call")
    override suspend fun decideApproval(device: Actor.Device, approvalId: Long, approve: Boolean) = null
    override suspend fun askAssistant(device: Actor.Device, message: String) = AssistantResponse("answer", "hello $message")
    override fun events(): Flow<EventDto> = eventFlow
}

class GatewayModuleTest {
    private val backend = FakeBackend()

    private fun test(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { gatewayModule(backend) }
        block()
    }

    @Test
    fun `event stream does not disclose events after token revocation`() = eventAuthorizationTest(false)

    @Test
    fun `event stream does not disclose events after read scope removed`() = eventAuthorizationTest(true)

    private fun eventAuthorizationTest(removeScope: Boolean) = test {
        backend.eventFlow = flow {
            emit(EventDto("before", 1, emptyMap()))
            if (removeScope) backend.loseReadScope = true else backend.revoked = true
            emit(EventDto("private-after", 2, emptyMap()))
        }
        val ws = createClient { install(ClientWebSockets) }
        withTimeout(5000) {
            ws.webSocket("/v1/Events", request = { header("Authorization", "Bearer full") }) {
                val first = incoming.receive() as Frame.Text
                assertTrue(first.readText().contains("before"))
                val remaining = mutableListOf<String>()
                for (frame in incoming) if (frame is Frame.Text) remaining += frame.readText()
                assertTrue(remaining.none { it.contains("private-after") })
                assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, closeReason.await()?.code)
            }
        }
        ws.close()
    }

    @Test
    fun `health needs no auth`() = test {
        val res = client.get("/v1/Health")
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("\"status\":\"ok\""))
    }

    @Test
    fun `missing or bad token is 401`() = test {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/Messages").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/Messages") { header("Authorization", "Bearer nope") }.status)
    }

    @Test
    fun `read scope lists messages with twilio shape`() = test {
        val body = client.get("/v1/Messages") { header("Authorization", "Bearer ro") }.bodyAsText()
        assertTrue(body.contains("\"sid\":\"SM1\""))
        assertTrue(body.contains("\"date_created\":1"))
    }

    @Test
    fun `sending sms without scope is 403`() = test {
        val res = client.post("/v1/Messages") {
            header("Authorization", "Bearer ro")
            contentType(ContentType.Application.Json)
            setBody("""{"to":"+15550001111","body":"hi"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, res.status)
        assertTrue(backend.sent.isEmpty())
    }

    @Test
    fun `sms waiting for approval is 202 with approval sid`() = test {
        val res = client.post("/v1/Messages") {
            header("Authorization", "Bearer full")
            contentType(ContentType.Application.Json)
            setBody("""{"to":"+15550001111","body":"hi"}""")
        }
        assertEquals(HttpStatusCode.Accepted, res.status)
        assertTrue(res.bodyAsText().contains("\"approval_sid\":\"AP7\""))
    }

    @Test
    fun `done sms is 201 with message sid`() = test {
        backend.smsResult = ActionResult.Done("42")
        val res = client.post("/v1/Messages") {
            header("Authorization", "Bearer full")
            contentType(ContentType.Application.Json)
            setBody("""{"to":"+15550001111","body":"hi"}""")
        }
        assertEquals(HttpStatusCode.Created, res.status)
        assertTrue(res.bodyAsText().contains("\"sid\":\"SM42\""))
    }

    @Test
    fun `invalid input is rejected before reaching the phone`() = test {
        for (payload in listOf("""{"to":"rm -rf /","body":"hi"}""", """{"to":"+1555","body":""}""", """{"oops":1}""", "not json")) {
            val res = client.post("/v1/Messages") {
                header("Authorization", "Bearer full")
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            assertEquals("payload $payload", HttpStatusCode.BadRequest, res.status)
        }
        assertTrue(backend.sent.isEmpty())
    }

    @Test
    fun `pairing with right and wrong code`() = test {
        val ok = client.post("/v1/Pair") { contentType(ContentType.Application.Json); setBody("""{"code":"123456","name":"laptop"}""") }
        assertEquals(HttpStatusCode.Created, ok.status)
        assertTrue(ok.bodyAsText().contains("oc_new"))
        val bad = client.post("/v1/Pair") { contentType(ContentType.Application.Json); setBody("""{"code":"000000","name":"laptop"}""") }
        assertEquals(HttpStatusCode.Unauthorized, bad.status)
    }

    @Test
    fun `approving needs approve scope and unknown call action is 422`() = test {
        val approve = client.post("/v1/Approvals/AP1") {
            header("Authorization", "Bearer full")
            contentType(ContentType.Application.Json)
            setBody("""{"decision":"approve"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, approve.status)
        val hangup = client.post("/v1/Calls/CA9") {
            header("Authorization", "Bearer full")
            contentType(ContentType.Application.Json)
            setBody("""{"action":"hangup"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, hangup.status)
    }

    @Test
    fun `place call passes agent goal and rejects oversized goals`() = test {
        val ok = client.post("/v1/Calls") {
            header("Authorization", "Bearer full")
            contentType(ContentType.Application.Json)
            setBody("""{"to":"+15550001111","agent_goal":"Book a table for 2 at 8pm"}""")
        }
        assertEquals(HttpStatusCode.Created, ok.status)
        assertEquals("Book a table for 2 at 8pm", backend.lastGoal)
        val tooLong = client.post("/v1/Calls") {
            header("Authorization", "Bearer full")
            contentType(ContentType.Application.Json)
            setBody("""{"to":"+15550001111","agent_goal":"${"x".repeat(501)}"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, tooLong.status)
    }

    @Test
    fun `phone number validation and sids`() {
        assertTrue(isPhoneNumber("+91 98765-43210"))
        assertTrue(!isPhoneNumber("12"))
        assertTrue(!isPhoneNumber("+1555;DROP"))
        assertEquals("CA3", Sids.call("call-3"))
        assertEquals("call-3", Sids.callId("CA3"))
        assertEquals(null, Sids.callId("XX3"))
        assertEquals(5L, Sids.parseLong("SM", "SM5"))
        assertEquals(null, Sids.parseLong("SM", "AP5"))
    }

    @Test
    fun `assistant endpoint requires read scope and returns a response`() = test {
        val res = client.post("/v1/Assistant") {
            header("Authorization", "Bearer ro")
            contentType(ContentType.Application.Json)
            setBody("""{"message":"what should I do?"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("hello what should I do?"))
    }
}
