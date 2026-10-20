package com.opencell.policy

/** The persisted call body format also used by approvals created before goal isolation. */
object CallGoal {
    private const val PREFIX = "AI voice agent goal: "

    fun body(goal: String?): String? = goal?.let { PREFIX + it }

    fun fromBody(body: String?): String? = body?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)
}
