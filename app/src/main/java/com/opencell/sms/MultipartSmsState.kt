package com.opencell.sms

/** Per-segment results, persisted until terminal. Failure is sticky; duplicate success is harmless. */
internal data class MultipartSmsState(val sent: String, val delivered: String) {
    val failed: Boolean get() = 'F' in sent || 'F' in delivered
    val sentComplete: Boolean get() = sent.all { it == 'S' }
    val deliveredComplete: Boolean get() = delivered.all { it == 'S' }
    val complete: Boolean get() = failed || (sentComplete && deliveredComplete)

    fun record(part: Int, isDelivery: Boolean, result: Char): MultipartSmsState {
        if (part !in sent.indices || result !in "PSF") return this
        fun update(value: String): String = value.toCharArray().also {
            // A temporary report must not undo a terminal result.
            if (it[part] != 'F' && (result != 'P' || it[part] == 'P')) it[part] = result
        }.concatToString()
        return if (isDelivery) copy(delivered = update(delivered)) else copy(sent = update(sent))
    }

    fun encode(): String = "$sent|$delivered"

    companion object {
        fun pending(total: Int): MultipartSmsState {
            require(total > 0)
            return MultipartSmsState("P".repeat(total), "P".repeat(total))
        }

        fun decode(value: String): MultipartSmsState? {
            val parts = value.split('|')
            if (parts.size != 2 || parts[0].isEmpty() || parts[0].length != parts[1].length ||
                parts.any { segment -> segment.any { it !in "PSF" } }) return null
            return MultipartSmsState(parts[0], parts[1])
        }
    }
}

/** GSM TP-Status 0x20..0x3f means still trying, not delivered. Missing reports stay pending. */
internal fun smsDeliveryResult(resultOk: Boolean, status: Int?): Char = when {
    !resultOk -> 'F'
    status == null || status < 0 -> 'P'
    status >= 0x40 -> 'F'
    status >= 0x20 -> 'P'
    else -> 'S'
}

/** Includes setup in the attempt, so an inserted outbox row never implies a successful send. */
internal fun sendSmsOrNull(id: Long, send: () -> Unit, markFailed: () -> Unit): Long? = try {
    send()
    id
} catch (_: Exception) {
    runCatching { markFailed() }
    null
}
