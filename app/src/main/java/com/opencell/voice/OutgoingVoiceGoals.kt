package com.opencell.voice

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Goals are armed only during execution and claimed by an opaque Telecom call token, never a number. */
internal class OutgoingVoiceGoals(private val now: () -> Long = System::currentTimeMillis) {
    private data class Pending(val goal: String, val atMillis: Long)
    private val pending = ConcurrentHashMap<String, Pending>()

    fun placeCall(goal: String?, place: (String?) -> Boolean): Boolean {
        if (goal == null) return place(null)
        pending.entries.removeIf { now() - it.value.atMillis >= TTL_MS }
        val token = UUID.randomUUID().toString()
        pending[token] = Pending(goal.take(500), now())
        var placed = false
        try {
            placed = place(token)
            return placed
        } finally {
            if (!placed) pending.remove(token)
        }
    }

    fun take(token: String?): String? = token?.let { pending.remove(it) }
        ?.takeIf { now() - it.atMillis < TTL_MS }?.goal

    fun discard(token: String?) { token?.let { pending.remove(it) } }

    private companion object {
        const val TTL_MS = 10 * 60_000L
    }
}
