package com.opencell.gateway

import com.opencell.AppContainer
import com.opencell.AppEvent
import com.opencell.policy.ActionRequest
import com.opencell.policy.ActionResult
import com.opencell.policy.ActionType
import com.opencell.policy.Actor
import com.opencell.policy.ApprovalEntity
import com.opencell.sms.SmsMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AndroidGatewayBackend(private val container: AppContainer, override val version: String) : GatewayBackend {

    override suspend fun authenticate(token: String) = container.pairingManager.authenticate(token)

    override suspend fun pair(code: String, name: String) = container.pairingManager.pair(code, name)

    override suspend fun listMessages(limit: Int) = container.smsRepository.recentMessages(limit).map { it.toDto() }

    override suspend fun message(id: Long) = container.smsRepository.message(id)?.toDto()

    override suspend fun listCalls(limit: Int) = container.unifiedCalls().first().take(limit).map {
        CallLogDto(it.id, it.source.name.lowercase(), it.kind.name.lowercase(), it.peerName, it.peerNumber, it.timestampMillis, it.durationSeconds)
    }

    override fun activeCalls() = container.callManager.activeCalls.value.map {
        ActiveCallDto(Sids.call(it.id), it.number, it.name, it.state.name.lowercase(), if (it.isIncoming) "inbound" else "outbound")
    }

    override suspend fun listApprovals() = container.database.policyDao().recentApprovals().map { it.toDto() }

    override suspend fun submitSms(device: Actor.Device, to: String, body: String) =
        container.actionExecutor.submit(ActionRequest(device, ActionType.SEND_SMS, to, body))

    override suspend fun submitCall(device: Actor.Device, to: String) =
        container.actionExecutor.submit(ActionRequest(device, ActionType.PLACE_CALL, to))

    override suspend fun submitCallAction(device: Actor.Device, callSid: String, action: String): ActionResult {
        val callId = Sids.callId(callSid) ?: return ActionResult.Failed("no such call")
        val type = when (action) {
            "answer" -> ActionType.ANSWER_CALL
            "reject" -> ActionType.REJECT_CALL
            else -> ActionType.HANGUP_CALL
        }
        return container.actionExecutor.submit(ActionRequest(device, type, callId))
    }

    override suspend fun decideApproval(device: Actor.Device, approvalId: Long, approve: Boolean) =
        container.actionExecutor.decide(approvalId, approve, decidedBy = device.label)?.toDto()

    override fun events(): Flow<EventDto> = container.events.map { it.toDto() }

    private fun SmsMessage.toDto() = MessageDto(
        sid = Sids.message(id),
        from = if (isIncoming) address else "me",
        to = if (isIncoming) "me" else address,
        body = body,
        status = status.name.lowercase(),
        direction = if (isIncoming) "inbound" else "outbound",
        dateCreated = timestampMillis,
    )

    private fun ApprovalEntity.toDto() = ApprovalDto(
        sid = Sids.approval(id),
        actor = actorLabel,
        action = action.name.lowercase(),
        target = target,
        body = body,
        status = status.name.lowercase(),
        dateCreated = createdAtMillis,
        result = resultRef,
    )

    private fun AppEvent.toDto(): EventDto = when (this) {
        is AppEvent.SmsReceived -> EventDto("sms.received", timestampMillis, mapOf("sid" to Sids.message(messageId), "from" to from, "body" to body))
        is AppEvent.CallStateChanged -> EventDto(
            "call.state",
            timestampMillis,
            mapOf("sid" to Sids.call(callId), "number" to number, "status" to state, "direction" to if (isIncoming) "inbound" else "outbound"),
        )
        is AppEvent.ApprovalChanged -> EventDto(
            "approval.updated",
            timestampMillis,
            mapOf("sid" to Sids.approval(approvalId), "status" to status, "action" to action, "target" to target),
        )
    }
}
