package com.opencell.policy

import java.time.LocalDate
import java.time.ZoneId

sealed interface ActionResult {
    /** [ref] is e.g. the SMS provider id or the call id. */
    data class Done(val ref: String?) : ActionResult
    data class Pending(val approvalId: Long) : ActionResult
    data class Denied(val reason: String) : ActionResult
    data class Failed(val reason: String) : ActionResult
}

/** The side effects an approved action can trigger; implemented with SmsRepository / Telecom on device. */
interface ActionHandlers {
    /** Returns the SMS provider id, or null on failure. */
    suspend fun sendSms(to: String, body: String): Long?
    fun placeCall(to: String): Boolean
    fun controlCall(type: ActionType, callId: String): Boolean
}

/** Shows / removes the Approve-Deny prompt (a notification on device). */
interface ApprovalPrompter {
    fun show(approval: ApprovalEntity)
    fun dismiss(approvalId: Long)
}

/** Every call/SMS action by the AI, a paired computer, or the user goes through here. */
class ActionExecutor(
    private val dao: PolicyDao,
    private val policySettings: suspend () -> PolicySettings,
    private val handlers: ActionHandlers,
    private val prompter: ApprovalPrompter,
    private val onApprovalChanged: (ApprovalEntity) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun submit(request: ActionRequest): ActionResult {
        val actionsToday = dao.countOutboundSince(startOfToday())
        return when (val decision = ActionPolicy.decide(request, policySettings(), actionsToday)) {
            Decision.Allow -> execute(request)
            Decision.Ask -> queueForApproval(request)
            is Decision.Deny -> {
                audit(request.actor.label, request, Outcome.DENIED, decision.reason)
                ActionResult.Denied(decision.reason)
            }
        }
    }

    /** User tapped Approve/Deny (or a computer with the approve scope did). Returns null if not pending. */
    suspend fun decide(approvalId: Long, approve: Boolean, decidedBy: String): ApprovalEntity? {
        val approval = dao.approval(approvalId)?.takeIf { it.status == ApprovalStatus.PENDING } ?: return null
        prompter.dismiss(approvalId)
        val updated = if (approve) {
            val request = ActionRequest(Actor.User, approval.action, approval.target, approval.body)
            val result = execute(request, actorLabel = "${approval.actorLabel} (approved by $decidedBy)")
            approval.copy(status = ApprovalStatus.APPROVED, resultRef = (result as? ActionResult.Done)?.ref ?: result.toString())
        } else {
            audit(approval.actorLabel, ActionRequest(Actor.User, approval.action, approval.target, approval.body), Outcome.DENIED, "denied by $decidedBy")
            approval.copy(status = ApprovalStatus.DENIED)
        }
        dao.updateApproval(updated)
        onApprovalChanged(updated)
        return updated
    }

    private suspend fun queueForApproval(request: ActionRequest): ActionResult {
        val approval = ApprovalEntity(
            createdAtMillis = now(),
            actorLabel = request.actor.label,
            deviceId = (request.actor as? Actor.Device)?.id,
            action = request.type,
            target = request.target,
            body = request.body,
        )
        val saved = approval.copy(id = dao.insertApproval(approval))
        audit(request.actor.label, request, Outcome.PENDING, "approval #${saved.id}")
        prompter.show(saved)
        onApprovalChanged(saved)
        return ActionResult.Pending(saved.id)
    }

    private suspend fun execute(request: ActionRequest, actorLabel: String = request.actor.label): ActionResult {
        val result = runCatching {
            when (request.type) {
                ActionType.SEND_SMS -> handlers.sendSms(request.target, request.body.orEmpty())
                    ?.let { ActionResult.Done(it.toString()) } ?: ActionResult.Failed("SMS could not be queued")
                ActionType.PLACE_CALL ->
                    if (handlers.placeCall(request.target)) ActionResult.Done(null) else ActionResult.Failed("call could not be placed")
                ActionType.ANSWER_CALL, ActionType.REJECT_CALL, ActionType.HANGUP_CALL ->
                    if (handlers.controlCall(request.type, request.target)) ActionResult.Done(request.target) else ActionResult.Failed("no such call")
            }
        }.getOrElse { ActionResult.Failed(it.message ?: it.javaClass.simpleName) }
        val (outcome, detail) = when (result) {
            is ActionResult.Done -> Outcome.DONE to result.ref
            is ActionResult.Failed -> Outcome.FAILED to result.reason
            else -> Outcome.FAILED to result.toString()
        }
        audit(actorLabel, request, outcome, detail)
        return result
    }

    private suspend fun audit(actor: String, request: ActionRequest, outcome: Outcome, detail: String?) {
        dao.insertAudit(AuditEntry(atMillis = now(), actor = actor, action = request.type, target = request.target, body = request.body, outcome = outcome, detail = detail))
    }

    private fun startOfToday(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
