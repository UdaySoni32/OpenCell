package com.opencell.calllog

import android.content.ContentResolver
import android.provider.CallLog
import com.opencell.data.observeQuery
import kotlinx.coroutines.flow.Flow

/** The system call log (SIM calls). */
class SimCallLogSource(private val resolver: ContentResolver) {

    fun observe(limit: Int = 500): Flow<List<CallEntry>> =
        resolver.observeQuery(CallLog.Calls.CONTENT_URI, emptyList()) { query(limit) }

    private fun query(limit: Int): List<CallEntry> {
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
        )
        val entries = mutableListOf<CallEntry>()
        resolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
            while (c.moveToNext() && entries.size < limit) {
                entries += CallEntry(
                    id = "sim-${c.getLong(0)}",
                    source = CallSource.SIM,
                    kind = kindOf(c.getInt(3)),
                    peerName = c.getString(2)?.takeIf { it.isNotBlank() },
                    peerNumber = c.getString(1)?.takeIf { it.isNotBlank() },
                    timestampMillis = c.getLong(4),
                    durationSeconds = c.getLong(5),
                )
            }
        }
        return entries
    }

    private fun kindOf(type: Int): CallKind = when (type) {
        CallLog.Calls.OUTGOING_TYPE -> CallKind.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallKind.MISSED
        CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> CallKind.REJECTED
        else -> CallKind.INCOMING
    }
}
