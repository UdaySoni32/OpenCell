package com.opencell.sms

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import com.opencell.data.observeQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

enum class SmsStatus { RECEIVED, PENDING, SENT, DELIVERED, FAILED }

data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val timestampMillis: Long,
    val isIncoming: Boolean,
    val status: SmsStatus,
    val isRead: Boolean,
)

data class SmsThread(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val timestampMillis: Long,
    val unreadCount: Int,
)

/**
 * Reads and writes the system SMS provider. As default SMS app we must write incoming and sent messages ourselves.
 */
class SmsRepository(private val context: Context) {

    private val resolver = context.contentResolver

    fun observeThreads(): Flow<List<SmsThread>> =
        resolver.observeQuery(Telephony.Sms.CONTENT_URI, emptyList()) { groupIntoThreads(queryMessages(null, null, MAX_SCAN)) }

    fun observeMessages(threadId: Long): Flow<List<SmsMessage>> =
        resolver.observeQuery(Telephony.Sms.CONTENT_URI, emptyList()) {
            queryMessages("${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), MAX_SCAN).reversed()
        }

    suspend fun recentMessages(limit: Int): List<SmsMessage> = withContext(Dispatchers.IO) { queryMessages(null, null, limit) }

    suspend fun message(id: Long): SmsMessage? = withContext(Dispatchers.IO) {
        queryMessages("${Telephony.Sms._ID} = ?", arrayOf(id.toString()), 1).firstOrNull()
    }

    suspend fun threadIdFor(address: String): Long = withContext(Dispatchers.IO) {
        Telephony.Threads.getOrCreateThreadId(context, address)
    }

    /** Writes an incoming SMS to the inbox; returns its provider id. */
    suspend fun storeIncoming(address: String, body: String, timestampMillis: Long): Long? = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, timestampMillis)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull()
    }

    /**
     * Queues [body] to [address]: writes it to the outbox, then sends it. Status updates arrive in [SmsStatusReceiver].
     * Returns the provider id, or null if the address/body is blank or the write failed.
     */
    suspend fun send(address: String, body: String): Long? = withContext(Dispatchers.IO) {
        val to = address.trim()
        if (to.isEmpty() || body.isBlank()) return@withContext null
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, to)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_PENDING)
        }
        val uri = resolver.insert(Telephony.Sms.CONTENT_URI, values) ?: return@withContext null
        val sms = context.getSystemService(SmsManager::class.java)
        val parts = sms.divideMessage(body)
        val sent = ArrayList(parts.indices.map { statusIntent(SmsStatusReceiver.ACTION_SENT, uri, it, parts.size) })
        val delivered = ArrayList(parts.indices.map { statusIntent(SmsStatusReceiver.ACTION_DELIVERED, uri, it, parts.size) })
        runCatching { sms.sendMultipartTextMessage(to, null, parts, sent, delivered) }
            .onFailure { markType(uri, Telephony.Sms.MESSAGE_TYPE_FAILED) }
        uri.lastPathSegment?.toLongOrNull()
    }

    suspend fun markThreadRead(threadId: Long) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        resolver.update(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString()))
    }

    fun markType(uri: Uri, type: Int) {
        resolver.update(uri, ContentValues().apply { put(Telephony.Sms.TYPE, type) }, null, null)
    }

    fun markStatus(uri: Uri, status: Int) {
        resolver.update(uri, ContentValues().apply { put(Telephony.Sms.STATUS, status) }, null, null)
    }

    private fun statusIntent(action: String, uri: Uri, part: Int, total: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        (uri.toString() + action + part).hashCode(),
        Intent(context, SmsStatusReceiver::class.java)
            .setAction(action)
            .setData(uri)
            .putExtra(SmsStatusReceiver.EXTRA_PART, part)
            .putExtra(SmsStatusReceiver.EXTRA_TOTAL, total),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun queryMessages(selection: String?, args: Array<String>?, limit: Int): List<SmsMessage> {
        val projection = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
            Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.STATUS, Telephony.Sms.READ,
        )
        val out = mutableListOf<SmsMessage>()
        resolver.query(Telephony.Sms.CONTENT_URI, projection, selection, args, "${Telephony.Sms.DATE} DESC")?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val type = c.getInt(5)
                out += SmsMessage(
                    id = c.getLong(0),
                    threadId = c.getLong(1),
                    address = c.getString(2).orEmpty(),
                    body = c.getString(3).orEmpty(),
                    timestampMillis = c.getLong(4),
                    isIncoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                    status = statusOf(type, c.getInt(6)),
                    isRead = c.getInt(7) == 1,
                )
            }
        }
        return out
    }

    companion object {
        private const val MAX_SCAN = 3000

        fun statusOf(type: Int, status: Int): SmsStatus = when (type) {
            Telephony.Sms.MESSAGE_TYPE_INBOX -> SmsStatus.RECEIVED
            Telephony.Sms.MESSAGE_TYPE_FAILED -> SmsStatus.FAILED
            Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> SmsStatus.PENDING
            else -> if (status == Telephony.Sms.STATUS_COMPLETE) SmsStatus.DELIVERED else SmsStatus.SENT
        }

        /** Newest message per thread, newest thread first. Input must be newest-first. */
        fun groupIntoThreads(messagesNewestFirst: List<SmsMessage>): List<SmsThread> =
            messagesNewestFirst.groupBy { it.threadId }.map { (threadId, msgs) ->
                val latest = msgs.first()
                SmsThread(
                    threadId = threadId,
                    address = latest.address,
                    snippet = latest.body,
                    timestampMillis = latest.timestampMillis,
                    unreadCount = msgs.count { it.isIncoming && !it.isRead },
                )
            }.sortedByDescending { it.timestampMillis }
    }
}
