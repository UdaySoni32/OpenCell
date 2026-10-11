package com.opencell.calllog

import com.opencell.calllog.notification.CallEvent
import com.opencell.calllog.notification.CallEventType

/**
 * Folds a stream of call notification events into one row per call:
 * RINGING → (ONGOING ⇒ INCOMING answered) | (MISSED ⇒ MISSED). An ONGOING with no prior ring is an OUTGOING call.
 */
class AppCallRecorder(private val dao: AppCallDao) {

    suspend fun onEvent(event: CallEvent) {
        val recent = dao.findRecent(event.packageName, event.peerName, event.timestampMillis - SAME_CALL_WINDOW_MS)
        when (event.type) {
            CallEventType.RINGING -> if (recent == null) insert(event, CallKind.RINGING)
            CallEventType.ONGOING -> when {
                recent == null -> insert(event, CallKind.OUTGOING, answeredAt = event.timestampMillis)
                recent.kind == CallKind.RINGING ->
                    dao.update(recent.copy(kind = CallKind.INCOMING, answeredAtMillis = event.timestampMillis))
                else -> Unit
            }
            CallEventType.MISSED -> when {
                recent == null -> insert(event, CallKind.MISSED)
                recent.kind == CallKind.RINGING -> dao.update(recent.copy(kind = CallKind.MISSED))
                else -> Unit
            }
        }
    }

    /** An ongoing-call notification went away: the call ended, so record its duration. */
    suspend fun onCallEnded(packageName: String, peerName: String?, endedAtMillis: Long) {
        val recent = dao.findRecent(packageName, peerName, endedAtMillis - MAX_CALL_LENGTH_MS) ?: return
        val answeredAt = recent.answeredAtMillis ?: return
        if (recent.durationSeconds > 0) return
        dao.update(recent.copy(durationSeconds = ((endedAtMillis - answeredAt) / 1000).coerceAtLeast(0)))
    }

    /** Records a call that arrived through Telecom as a self-managed call (no notification parsing needed). */
    suspend fun onSelfManagedCall(packageName: String, peerName: String?, kind: CallKind, atMillis: Long, durationSeconds: Long) {
        dao.insert(
            AppCallEntity(
                packageName = packageName,
                kind = kind,
                peerName = peerName,
                startedAtMillis = atMillis,
                durationSeconds = durationSeconds,
            ),
        )
    }

    private suspend fun insert(event: CallEvent, kind: CallKind, answeredAt: Long? = null) {
        dao.insert(
            AppCallEntity(
                packageName = event.packageName,
                kind = kind,
                peerName = event.peerName,
                startedAtMillis = event.timestampMillis,
                answeredAtMillis = answeredAt,
                notificationKey = event.notificationKey,
            ),
        )
    }

    companion object {
        const val SAME_CALL_WINDOW_MS = 3 * 60_000L
        const val MAX_CALL_LENGTH_MS = 6 * 60 * 60_000L
    }
}
