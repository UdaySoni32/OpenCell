package com.opencell.calllog.notification

import com.opencell.calllog.CallSource

/** Plain copy of the notification fields the parser needs, so parsing is testable on the JVM. */
data class NotificationSnapshot(
    val key: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    val category: String?,
    val isOngoing: Boolean,
    val postTimeMillis: Long,
)

enum class CallEventType { RINGING, ONGOING, MISSED }

data class CallEvent(
    val source: CallSource,
    val packageName: String,
    val type: CallEventType,
    val peerName: String?,
    val timestampMillis: Long,
    val notificationKey: String,
)

/**
 * Turns other apps' call notifications into [CallEvent]s.
 *
 * shortcut: keyword heuristics in English only; replace with per-app fixtures captured on real devices
 * (Phase 0) and add localized keywords when users report misses.
 */
object CallNotificationParser {

    private const val CATEGORY_CALL = "call"
    private const val CATEGORY_MISSED_CALL = "missed_call"

    private val missedRegex = Regex("""\bmissed\b.*\bcalls?\b|\b\d+\s+missed\b""", RegexOption.IGNORE_CASE)
    private val missedCountOnly = Regex("""^\s*\d+\s+missed\s+(voice\s+|video\s+)?calls?\s*$""", RegexOption.IGNORE_CASE)
    private val ringingRegex = Regex("""\bincoming\b|\bcalling\b|\bringing\b|\bis calling you\b""", RegexOption.IGNORE_CASE)
    private val ongoingRegex = Regex("""\bongoing\b|\bon call\b|\bconnected\b|\bin call\b|\b\d{1,2}:\d{2}\b""", RegexOption.IGNORE_CASE)
    private val callWord = Regex("""\b(voice|video|audio)?\s*call\b""", RegexOption.IGNORE_CASE)
    private val genericTitles = Regex(
        """^(missed (voice |video |group )?calls?|incoming (voice |video )?call|ongoing (voice |video )?call|whatsapp|telegram|signal|discord)$""",
        RegexOption.IGNORE_CASE,
    )

    // SIM calls come from the system call log; their dialers' notifications would be double-counted as app calls.
    private val simDialerPackages = setOf(
        "com.android.server.telecom", "com.google.android.telecomui", "com.android.dialer", "com.google.android.dialer", "com.android.incallui",
        "com.samsung.android.incallui", "com.samsung.android.dialer", "com.android.phone", "com.oneplus.dialer",
        "com.miui.voip", "com.android.contacts",
    )

    fun parse(n: NotificationSnapshot): CallEvent? {
        if (n.packageName in simDialerPackages) return null
        val source = CallSource.fromPackage(n.packageName)
        val title = n.title?.trim().orEmpty()
        val text = n.text?.trim().orEmpty()
        val both = "$title $text"

        val type = when {
            n.category == CATEGORY_MISSED_CALL || missedRegex.containsMatchIn(both) -> CallEventType.MISSED
            n.category == CATEGORY_CALL && n.isOngoing && ongoingRegex.containsMatchIn(both) -> CallEventType.ONGOING
            n.category == CATEGORY_CALL -> CallEventType.RINGING
            source != CallSource.OTHER && ringingRegex.containsMatchIn(both) && callWord.containsMatchIn(both) ->
                CallEventType.RINGING
            else -> return null
        }
        return CallEvent(
            source = source,
            packageName = n.packageName,
            type = type,
            peerName = peerName(title, text),
            timestampMillis = n.postTimeMillis,
            notificationKey = n.key,
        )
    }

    /** The peer is whichever of title/text is not a generic phrase like "Missed voice call". */
    private fun peerName(title: String, text: String): String? {
        val titleIsGeneric = title.isEmpty() || genericTitles.matches(title) || missedCountOnly.matches(title)
        if (!titleIsGeneric) return title
        if (missedCountOnly.matches(text)) return null
        val candidate = text
            .replace(Regex("""(?i)\s*(is calling( you)?|calling|missed (voice |video )?call( from)?)\s*"""), " ")
            .replace(Regex("""(?i)^from\s+"""), "")
            .trim()
        return candidate.takeIf { it.isNotEmpty() && !missedCountOnly.matches(it) && !genericTitles.matches(it) }
    }
}
