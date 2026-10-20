package com.opencell.policy

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** [goal] belongs only to this execution; null places an ordinary call. */
    fun placeCall(to: String, goal: String? = null): Boolean
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
    // Shared by submission and approval: the limit check, side effect and audit are one
    // serialized operation in the app's single ActionExecutor.
    private val actionMutex = Mutex()

    suspend fun submit(request: ActionRequest): ActionResult = actionMutex.withLock {
        val actionsToday = dao.countOutboundSince(startOfToday())
        when (val decision = ActionPolicy.decide(request, policySettings(), actionsToday)) {
            Decision.Allow -> execute(request)
            Decision.Ask -> queueForApproval(request)
            is Decision.Deny -> {
                audit(request.actor.label, request, Outcome.DENIED, decision.reason)
                ActionResult.Denied(decision.reason)
            }
        }
    }

    /**
     * Explicit approval bypasses automatic limits/asking. Local callers default to the user;
     * remote callers must supply their actor so the current kill switch can still block them.
     * Returns null if not pending, or if remote approval is blocked (leaving it pending).
     */
    suspend fun decide(
        approvalId: Long,
        approve: Boolean,
        decidedBy: String,
        decidingActor: Actor = Actor.User,
    ): ApprovalEntity? = actionMutex.withLock {
        val approval = dao.approval(approvalId)?.takeIf { it.status == ApprovalStatus.PENDING } ?: return@withLock null
        if (approve && decidingActor != Actor.User && policySettings().killSwitch) {
            audit(decidingActor.label, ActionRequest(decidingActor, approval.action, approval.target, approval.body), Outcome.DENIED, "kill switch is on")
            return@withLock null
        }
        // Consume before any side effect. If execution is interrupted or persistence of its
        // result fails, retrying the decision must not send the action again.
        val decided = approval.copy(status = if (approve) ApprovalStatus.APPROVED else ApprovalStatus.DENIED)
        dao.updateApproval(decided)
        prompter.dismiss(approvalId)
        val updated = if (approve) {
            val request = ActionRequest(Actor.User, approval.action, approval.target, approval.body)
            val result = execute(request, actorLabel = "${approval.actorLabel} (approved by $decidedBy)")
            decided.copy(resultRef = (result as? ActionResult.Done)?.ref ?: result.toString())
        } else {
            audit(approval.actorLabel, ActionRequest(Actor.User, approval.action, approval.target, approval.body), Outcome.DENIED, "denied by $decidedBy")
            decided
        }
        dao.updateApproval(updated)
        onApprovalChanged(updated)
        updated
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
                    if (handlers.placeCall(request.target, CallGoal.fromBody(request.body))) ActionResult.Done(null) else ActionResult.Failed("call could not be placed")
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
