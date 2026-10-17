package com.opencell.gateway

import com.opencell.policy.Actor
import com.opencell.policy.ActionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Twilio-shaped wire format: snake_case fields, "sid" ids (SM… messages, CA… calls, AP… approvals).

@Serializable
data class MessageDto(
    val sid: String,
    val from: String,
    val to: String,
    val body: String,
    val status: String,
    val direction: String,
    @SerialName("date_created") val dateCreated: Long,
)

@Serializable
data class CallLogDto(
    val sid: String,
    val source: String,
    val kind: String,
    @SerialName("peer_name") val peerName: String?,
    @SerialName("peer_number") val peerNumber: String?,
    @SerialName("date_created") val dateCreated: Long,
    val duration: Long,
)

@Serializable
data class ActiveCallDto(val sid: String, val number: String?, val name: String?, val status: String, val direction: String)

@Serializable
data class ApprovalDto(
    val sid: String,
    val actor: String,
    val action: String,
    val target: String,
    val body: String?,
    val status: String,
    @SerialName("date_created") val dateCreated: Long,
    val result: String? = null,
)

@Serializable
data class SendMessageRequest(val to: String, val body: String)

/** [agentGoal]: if set, the phone's AI voice agent conducts the call toward this goal once it connects. */
@Serializable
data class PlaceCallRequest(val to: String, @SerialName("agent_goal") val agentGoal: String? = null)

@Serializable
data class CallActionRequest(val action: String)

@Serializable
data class ApprovalDecisionRequest(val decision: String)

@Serializable
data class PairRequest(val code: String, val name: String)

@Serializable
data class PairResponse(val token: String, @SerialName("device_id") val deviceId: Long, val scopes: List<String>)

/** Result of an action request: 201 done, 202 waiting for the user's approval, 403 denied, 422 failed. */
@Serializable
data class ActionResponse(
    val status: String,
    val sid: String? = null,
    @SerialName("approval_sid") val approvalSid: String? = null,
    val reason: String? = null,
)

@Serializable
data class EventDto(val type: String, @SerialName("date_created") val dateCreated: Long, val data: Map<String, String?>)

@Serializable
data class ErrorDto(val error: String)

@Serializable
data class HealthDto(val status: String, val app: String, val version: String)

@Serializable
data class AssistantRequest(val message: String)

@Serializable
data class AssistantResponse(
    val status: String,
    val reply: String,
    @SerialName("action_type") val actionType: String? = null,
    val to: String? = null,
    val body: String? = null,
)

/** Everything the HTTP layer needs from the phone. The Android implementation wires it to the app container. */
interface GatewayBackend {
    val version: String
    suspend fun authenticate(token: String): Actor.Device?
    /** Exchanges a one-time pairing code for a new device token; null if the code is wrong or expired. */
    suspend fun pair(code: String, name: String): PairResponse?
    suspend fun listMessages(limit: Int): List<MessageDto>
    suspend fun message(id: Long): MessageDto?
    suspend fun listCalls(limit: Int): List<CallLogDto>
    fun activeCalls(): List<ActiveCallDto>
    suspend fun listApprovals(): List<ApprovalDto>
    suspend fun submitSms(device: Actor.Device, to: String, body: String): ActionResult
    suspend fun submitCall(device: Actor.Device, to: String, agentGoal: String? = null): ActionResult
    suspend fun submitCallAction(device: Actor.Device, callSid: String, action: String): ActionResult
    suspend fun decideApproval(device: Actor.Device, approvalId: Long, approve: Boolean): ApprovalDto?
    /** Ask the assistant for an answer or an action draft; this endpoint never executes an action itself. */
    suspend fun askAssistant(device: Actor.Device, message: String): AssistantResponse =
        AssistantResponse("error", "Assistant gateway support is not available in this build.")
    fun events(): Flow<EventDto>
}

object Sids {
    fun message(id: Long) = "SM$id"
    fun approval(id: Long) = "AP$id"
    /** CallManager ids look like "call-3"; on the wire they are "CA3". */
    fun call(id: String) = "CA" + id.removePrefix("call-")
    fun callId(sid: String): String? = sid.takeIf { it.startsWith("CA") }?.removePrefix("CA")?.takeIf { it.isNotEmpty() }?.let { "call-$it" }
    fun parseLong(prefix: String, sid: String): Long? = sid.removePrefix(prefix).toLongOrNull()?.takeIf { sid.startsWith(prefix) }
}
