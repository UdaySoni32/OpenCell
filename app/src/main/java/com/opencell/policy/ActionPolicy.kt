package com.opencell.policy

/** Who wants something done. The user (tapping in the app) is always trusted; everyone else goes through policy. */
sealed interface Actor {
    val label: String

    data object User : Actor {
        override val label = "you"
    }

    data class Ai(val feature: String) : Actor {
        override val label = "AI ($feature)"
    }

    data class Device(val id: Long, val name: String, val scopes: Set<Scope>) : Actor {
        override val label = "computer \"$name\""
    }
}

enum class Scope(val wire: String) {
    READ("read"),
    SMS_SEND("sms:send"),
    CALL_PLACE("call:place"),
    CALL_CONTROL("call:control"),
    APPROVE("approve"),
    AUTO_APPROVE("auto-approve");

    companion object {
        val DEFAULT_DEVICE = setOf(READ, SMS_SEND, CALL_PLACE, CALL_CONTROL)
        fun parse(csv: String): Set<Scope> = csv.split(',').mapNotNull { s -> entries.firstOrNull { it.wire == s.trim() } }.toSet()
        fun format(scopes: Set<Scope>): String = scopes.sortedBy { it.ordinal }.joinToString(",") { it.wire }
    }
}

enum class ActionType(val requiredScope: Scope, val label: String) {
    SEND_SMS(Scope.SMS_SEND, "send SMS"),
    PLACE_CALL(Scope.CALL_PLACE, "place call"),
    ANSWER_CALL(Scope.CALL_CONTROL, "answer call"),
    REJECT_CALL(Scope.CALL_CONTROL, "reject call"),
    HANGUP_CALL(Scope.CALL_CONTROL, "hang up call"),
}

data class ActionRequest(val actor: Actor, val type: ActionType, val target: String, val body: String? = null)

enum class PolicyMode { ASK, ALLOWLIST, FULL_AUTO }

data class PolicySettings(
    val mode: PolicyMode = PolicyMode.ASK,
    val allowlist: Set<String> = emptySet(),
    val dailyLimit: Int = 50,
    val killSwitch: Boolean = false,
)

sealed interface Decision {
    data object Allow : Decision
    data object Ask : Decision
    data class Deny(val reason: String) : Decision
}

/** The single permission rule for every call/SMS action. Pure: all state comes in as arguments. */
object ActionPolicy {

    fun decide(request: ActionRequest, settings: PolicySettings, actionsToday: Int): Decision {
        val actor = request.actor
        if (actor is Actor.User) return Decision.Allow
        if (settings.killSwitch) return Decision.Deny("kill switch is on")
        if (actor is Actor.Device && request.type.requiredScope !in actor.scopes) {
            return Decision.Deny("missing scope ${request.type.requiredScope.wire}")
        }
        // Controlling a call that already exists (hang up, answer) is not an outbound action, so it skips limits/asking.
        val isOutbound = request.type == ActionType.SEND_SMS || request.type == ActionType.PLACE_CALL
        if (!isOutbound) return Decision.Allow
        val underLimit = actionsToday < settings.dailyLimit
        if (actor is Actor.Device && Scope.AUTO_APPROVE in actor.scopes) {
            return if (underLimit) Decision.Allow else Decision.Ask
        }
        return when (settings.mode) {
            PolicyMode.ASK -> Decision.Ask
            PolicyMode.ALLOWLIST -> if (underLimit && isAllowed(request.target, settings.allowlist)) Decision.Allow else Decision.Ask
            PolicyMode.FULL_AUTO -> if (underLimit) Decision.Allow else Decision.Ask
        }
    }

    /** Matches on the last 10 digits so "+91 98765 43210" and "09876543210" are the same number. */
    fun isAllowed(target: String, allowlist: Set<String>): Boolean {
        val key = numberKey(target) ?: return false
        return allowlist.any { numberKey(it) == key }
    }

    fun numberKey(number: String): String? = number.filter(Char::isDigit).takeLast(10).takeIf { it.length >= 3 }
}
