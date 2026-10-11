package com.opencell.calllog

/** Where a call happened. [packageName] is null only for SIM calls. */
enum class CallSource(val label: String, val packageName: String?) {
    SIM("Phone", null),
    WHATSAPP("WhatsApp", "com.whatsapp"),
    WHATSAPP_BUSINESS("WhatsApp Business", "com.whatsapp.w4b"),
    TELEGRAM("Telegram", "org.telegram.messenger"),
    SIGNAL("Signal", "org.thoughtcrime.securesms"),
    DISCORD("Discord", "com.discord"),
    MESSENGER("Messenger", "com.facebook.orca"),
    INSTAGRAM("Instagram", "com.instagram.android"),
    TEAMS("Teams", "com.microsoft.teams"),
    MEET("Google Meet", "com.google.android.apps.tachyon"),
    OTHER("Other app", null);

    companion object {
        fun fromPackage(packageName: String): CallSource =
            entries.firstOrNull { it.packageName == packageName } ?: OTHER
    }
}

enum class CallKind { INCOMING, OUTGOING, MISSED, REJECTED, RINGING }

/** One row of the unified call list, regardless of source. */
data class CallEntry(
    val id: String,
    val source: CallSource,
    val kind: CallKind,
    val peerName: String?,
    val peerNumber: String?,
    val timestampMillis: Long,
    val durationSeconds: Long,
    /** Package that owns the call; set for app calls (also for [CallSource.OTHER]). */
    val packageName: String? = source.packageName,
) {
    val displayName: String get() = peerName ?: peerNumber ?: "Unknown"
}

/** Newest first; SIM and app entries interleaved by time. */
fun mergeCallLogs(sim: List<CallEntry>, apps: List<CallEntry>): List<CallEntry> =
    (sim + apps).sortedByDescending { it.timestampMillis }
