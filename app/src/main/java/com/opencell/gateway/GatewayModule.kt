package com.opencell.gateway

import com.opencell.policy.ActionResult
import com.opencell.policy.Actor
import com.opencell.policy.Scope
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.serialization.json.Json

private val wireJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

private const val MAX_LIMIT = 500
private const val MAX_SMS_LENGTH = 1600
private const val MAX_GOAL_LENGTH = 500
private const val MAX_ASSISTANT_MESSAGE_LENGTH = 4000

/** HTTP + WebSocket API. Auth: `Authorization: Bearer <token>` (WebSocket may use `?token=`). */
fun Application.gatewayModule(backend: GatewayBackend) {
    install(ContentNegotiation) { json(wireJson) }
    install(WebSockets)
    install(StatusPages) {
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ErrorDto("malformed request body")) }
    }

    routing {
        get("/v1/Health") { call.respond(HealthDto("ok", "OpenCell", backend.version)) }

        post("/v1/Pair") {
            val req = call.receive<PairRequest>()
            val name = req.name.trim().take(64)
            if (name.isEmpty()) return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("name is required"))
            val result = backend.pair(req.code.trim(), name)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorDto("invalid or expired pairing code"))
            call.respond(HttpStatusCode.Created, result)
        }

        get("/v1/Messages") {
            call.authorized(backend, Scope.READ) ?: return@get
            call.respond(backend.listMessages(call.limit()))
        }

        get("/v1/Messages/{sid}") {
            call.authorized(backend, Scope.READ) ?: return@get
            val id = Sids.parseLong("SM", call.parameters["sid"].orEmpty())
            val msg = id?.let { backend.message(it) } ?: return@get call.respond(HttpStatusCode.NotFound, ErrorDto("no such message"))
            call.respond(msg)
        }

        post("/v1/Messages") {
            val device = call.authorized(backend, Scope.SMS_SEND) ?: return@post
            val req = call.receive<SendMessageRequest>()
            val to = req.to.trim()
            if (!isPhoneNumber(to)) return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("'to' must be a phone number"))
            if (req.body.isBlank() || req.body.length > MAX_SMS_LENGTH) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("'body' must be 1..$MAX_SMS_LENGTH characters"))
            }
            call.respondAction(backend.submitSms(device, to, req.body)) { ref -> ref?.toLongOrNull()?.let(Sids::message) }
        }

        get("/v1/Calls") {
            call.authorized(backend, Scope.READ) ?: return@get
            call.respond(backend.listCalls(call.limit()))
        }

        get("/v1/Calls/Active") {
            call.authorized(backend, Scope.READ) ?: return@get
            call.respond(backend.activeCalls())
        }

        post("/v1/Calls") {
            val device = call.authorized(backend, Scope.CALL_PLACE) ?: return@post
            val req = call.receive<PlaceCallRequest>()
            val to = req.to.trim()
            if (!isPhoneNumber(to)) return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("'to' must be a phone number"))
            val goal = req.agentGoal?.trim()?.takeIf { it.isNotEmpty() }
            if (goal != null && goal.length > MAX_GOAL_LENGTH) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("'agent_goal' must be at most $MAX_GOAL_LENGTH characters"))
            }
            call.respondAction(backend.submitCall(device, to, goal)) { null }
        }

        post("/v1/Calls/{sid}") {
            val device = call.authorized(backend, Scope.CALL_CONTROL) ?: return@post
            val action = call.receive<CallActionRequest>().action
            if (action !in setOf("answer", "reject", "hangup")) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("action must be answer, reject or hangup"))
            }
            call.respondAction(backend.submitCallAction(device, call.parameters["sid"].orEmpty(), action)) { ref -> ref?.let(Sids::call) }
        }

        get("/v1/Approvals") {
            call.authorized(backend, Scope.READ) ?: return@get
            call.respond(backend.listApprovals())
        }

        post("/v1/Assistant") {
            val device = call.authorized(backend, Scope.READ) ?: return@post
            val req = call.receive<AssistantRequest>()
            if (req.message.isBlank() || req.message.length > MAX_ASSISTANT_MESSAGE_LENGTH) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("'message' must be 1..$MAX_ASSISTANT_MESSAGE_LENGTH characters"))
            }
            call.respond(backend.askAssistant(device, req.message.trim()))
        }

        post("/v1/Approvals/{sid}") {
            val device = call.authorized(backend, Scope.APPROVE) ?: return@post
            val id = Sids.parseLong("AP", call.parameters["sid"].orEmpty())
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorDto("no such approval"))
            val decision = call.receive<ApprovalDecisionRequest>().decision
            if (decision !in setOf("approve", "deny")) return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("decision must be approve or deny"))
            val result = backend.decideApproval(device, id, decision == "approve")
                ?: return@post call.respond(HttpStatusCode.Conflict, ErrorDto("approval is not pending"))
            call.respond(result)
        }

        webSocket("/v1/Events") {
            val token = call.bearerToken() ?: call.request.queryParameters["token"]
            val device = token?.let { backend.authenticate(it) }
            if (device == null || Scope.READ !in device.scopes) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "unauthorized"))
                return@webSocket
            }
            backend.events().collect { send(Frame.Text(wireJson.encodeToString(EventDto.serializer(), it))) }
        }
    }
}

/** E.164-ish: optional +, then 3..20 digits/spaces/dashes/parens. Rejects anything that could smuggle other input. */
fun isPhoneNumber(s: String): Boolean = Regex("""^\+?[0-9 ()\-]{3,20}$""").matches(s) && s.count(Char::isDigit) >= 3

private fun ApplicationCall.bearerToken(): String? =
    request.headers["Authorization"]?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)?.trim()

private fun ApplicationCall.limit(): Int = (request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, MAX_LIMIT)

/** Returns the device, or responds 401/403 and returns null. */
private suspend fun ApplicationCall.authorized(backend: GatewayBackend, scope: Scope): Actor.Device? {
    val device = bearerToken()?.let { backend.authenticate(it) }
    if (device == null) {
        respond(HttpStatusCode.Unauthorized, ErrorDto("missing or invalid token"))
        return null
    }
    if (scope !in device.scopes) {
        respond(HttpStatusCode.Forbidden, ErrorDto("token lacks scope ${scope.wire}"))
        return null
    }
    return device
}

private suspend fun ApplicationCall.respondAction(result: ActionResult, sidOf: (String?) -> String?) = when (result) {
    is ActionResult.Done -> respond(HttpStatusCode.Created, ActionResponse("done", sid = sidOf(result.ref)))
    is ActionResult.Pending -> respond(HttpStatusCode.Accepted, ActionResponse("pending_approval", approvalSid = Sids.approval(result.approvalId)))
    is ActionResult.Denied -> respond(HttpStatusCode.Forbidden, ActionResponse("denied", reason = result.reason))
    is ActionResult.Failed -> respond(HttpStatusCode.UnprocessableEntity, ActionResponse("failed", reason = result.reason))
}
