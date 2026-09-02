package io.opencell.platform.messaging

import android.content.Context
import android.provider.Telephony
import android.telephony.SmsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.opencell.core.crypto.CryptoUtils
import io.opencell.core.database.dao.AuditLogDao
import io.opencell.core.database.dao.MessageDao
import io.opencell.core.database.entity.AuditLogEntity
import io.opencell.core.database.entity.MessageEntity
import io.opencell.core.database.entity.MessageEventEntity
import io.opencell.core.model.*
import io.opencell.platform.events.EventEngine
import io.opencell.platform.sms.SmsDeliveryReceiver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val messageDao: MessageDao,
    private val auditLogDao: AuditLogDao,
    private val eventEngine: EventEngine
) {
    suspend fun sendSms(
        to: String,
        body: String,
        deviceId: String,
        subscriptionId: Int? = null
    ): Result<Message> {
        val messageId = CryptoUtils.generateId("msg")
        val message = Message(
            id = messageId, deviceId = deviceId, subscriptionId = subscriptionId ?: 0,
            type = MessageType.SMS, direction = MessageDirection.OUTBOUND,
            sender = "", recipient = to, body = body,
            state = MessageState.CREATED, createdAt = System.currentTimeMillis()
        )

        return try {
            messageDao.upsertMessage(message.toMessageEntity())

            eventEngine.emit(EventNames.MESSAGE_CREATED, deviceId, mapOf(
                "message_id" to JsonPrimitive(messageId),
                "direction" to JsonPrimitive("OUTBOUND"),
                "to" to JsonPrimitive(to)
            ))

            messageDao.updateMessageState(messageId, MessageState.QUEUED.name)
            eventEngine.emit(EventNames.MESSAGE_QUEUED, deviceId, mapOf("message_id" to JsonPrimitive(messageId)))

            val smsManager = if (subscriptionId != null && subscriptionId > 0) {
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            } else {
                SmsManager.getDefault()
            }

            val sentIntent = android.content.Intent(SmsDeliveryReceiver.ACTION_SMS_SENT).apply {
                putExtra(SmsDeliveryReceiver.EXTRA_MESSAGE_ID, messageId)
                setPackage(context.packageName)
            }
            val deliveredIntent = android.content.Intent(SmsDeliveryReceiver.ACTION_SMS_DELIVERED).apply {
                putExtra(SmsDeliveryReceiver.EXTRA_MESSAGE_ID, messageId)
                setPackage(context.packageName)
            }
            val flags = android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT

            messageDao.updateMessageState(messageId, MessageState.SENDING.name)
            eventEngine.emit(EventNames.MESSAGE_SENDING, deviceId, mapOf("message_id" to JsonPrimitive(messageId)))

            val parts = smsManager.divideMessage(body)
            if (parts != null && parts.size > 1) {
                val sentIntents = parts.indices.map { i ->
                    android.app.PendingIntent.getBroadcast(context, messageId.hashCode() + i, sentIntent, flags)
                }
                val deliveredIntents = parts.indices.map { i ->
                    android.app.PendingIntent.getBroadcast(context, messageId.hashCode() + 1000 + i, deliveredIntent, flags)
                }
                smsManager.sendMultipartTextMessage(to, null, parts, ArrayList(sentIntents), ArrayList(deliveredIntents))
            } else {
                val piSent = android.app.PendingIntent.getBroadcast(context, messageId.hashCode(), sentIntent, flags)
                val piDelivered = android.app.PendingIntent.getBroadcast(context, messageId.hashCode() + 1000, deliveredIntent, flags)
                smsManager.sendTextMessage(to, null, body, piSent, piDelivered)
            }

            auditLogDao.insertEntry(AuditLogEntity(
                id = CryptoUtils.generateId("audit"), action = "message.sent",
                actorType = "api", actorId = "api_client", resourceType = "message",
                resourceId = messageId, details = """{"to":"$to","body_length":${body.length}}"""
            ))

            Result.success(message.copy(state = MessageState.SENDING, threadId = generateThreadId(to)))
        } catch (e: Exception) {
            messageDao.updateMessageState(messageId, MessageState.FAILED.name, null)
            messageDao.insertMessageEvent(MessageEventEntity(
                id = CryptoUtils.generateId("evt"), messageId = messageId,
                state = MessageState.FAILED.name, timestamp = System.currentTimeMillis(), details = e.message
            ))
            eventEngine.emit(EventNames.MESSAGE_FAILED, deviceId, mapOf(
                "message_id" to JsonPrimitive(messageId),
                "error" to JsonPrimitive(e.message ?: "Unknown error")
            ))
            Result.failure(e)
        }
    }

    /**
     * Called by [SmsDeliveryReceiver] when the network confirms the SMS was sent.
     */
    suspend fun onSmsSentStatus(messageId: String, success: Boolean, resultCode: Int, deviceId: String) {
        if (success) {
            messageDao.updateMessageState(messageId, MessageState.SENT.name, sentAt = System.currentTimeMillis())
            messageDao.insertMessageEvent(MessageEventEntity(
                id = CryptoUtils.generateId("evt"), messageId = messageId,
                state = MessageState.SENT.name, timestamp = System.currentTimeMillis()
            ))
            eventEngine.emit(EventNames.MESSAGE_SENT, deviceId, mapOf("message_id" to JsonPrimitive(messageId)))
        } else {
            val errorMsg = smsResultCodeToError(resultCode)
            messageDao.updateMessageState(messageId, MessageState.FAILED.name, null)
            messageDao.insertMessageEvent(MessageEventEntity(
                id = CryptoUtils.generateId("evt"), messageId = messageId,
                state = MessageState.FAILED.name, timestamp = System.currentTimeMillis(),
                details = errorMsg
            ))
            eventEngine.emit(EventNames.MESSAGE_FAILED, deviceId, mapOf(
                "message_id" to JsonPrimitive(messageId),
                "error" to JsonPrimitive(errorMsg)
            ))
        }
    }

    /**
     * Called by [SmsDeliveryReceiver] when the recipient's handset confirms receipt.
     */
    suspend fun onSmsDeliveredStatus(messageId: String, deviceId: String) {
        messageDao.markDelivered(messageId)
        messageDao.insertMessageEvent(MessageEventEntity(
            id = CryptoUtils.generateId("evt"), messageId = messageId,
            state = MessageState.DELIVERED.name, timestamp = System.currentTimeMillis()
        ))
        eventEngine.emit(EventNames.MESSAGE_DELIVERED, deviceId, mapOf("message_id" to JsonPrimitive(messageId)))
    }

    suspend fun onIncomingSms(
        sender: String,
        body: String,
        deviceId: String,
        timestamp: Long = System.currentTimeMillis(),
        subscriptionId: Int = 0
    ) {
        val messageId = CryptoUtils.generateId("msg")
        val threadId = generateThreadId(sender)

        val message = MessageEntity(
            id = messageId, deviceId = deviceId, subscriptionId = subscriptionId,
            type = MessageType.SMS.name, direction = MessageDirection.INBOUND.name,
            sender = sender, recipient = "", body = body,
            state = MessageState.RECEIVED.name, createdAt = timestamp, threadId = threadId
        )
        messageDao.upsertMessage(message)

        messageDao.insertMessageEvent(MessageEventEntity(
            id = CryptoUtils.generateId("evt"), messageId = messageId,
            state = MessageState.RECEIVED.name, timestamp = timestamp
        ))

        eventEngine.emit(EventNames.MESSAGE_RECEIVED, deviceId, mapOf(
            "message_id" to JsonPrimitive(messageId),
            "from" to JsonPrimitive(sender),
            "body_preview" to JsonPrimitive(body.take(100))
        ))

        auditLogDao.insertEntry(AuditLogEntity(
            id = CryptoUtils.generateId("audit"), action = "message.received",
            actorType = "system", actorId = "sms_receiver", resourceType = "message",
            resourceId = messageId, details = """{"from":"$sender"}"""
        ))
    }

    /**
     * Import recent SMS messages from the system SMS provider (default SMS app history).
     * This populates the messages list with SMS sent/received before OpenCell was installed.
     */
    suspend fun importSystemSmsHistory(deviceId: String) {
        try {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ
            )
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null, null,
                "${Telephony.Sms.DATE} DESC"
            )
            cursor?.use { c ->
                val idIdx = c.getColumnIndex(Telephony.Sms._ID)
                val addrIdx = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = c.getColumnIndex(Telephony.Sms.TYPE)

                var count = 0
                while (c.moveToNext() && count < 100) {
                    val sysId = c.getString(idIdx) ?: continue
                    val address = c.getString(addrIdx) ?: ""
                    val body = c.getString(bodyIdx) ?: continue
                    val date = c.getLong(dateIdx)
                    val type = c.getInt(typeIdx)

                    // Skip if we already have this message (by checking thread + timestamp)
                    val threadId = generateThreadId(address)
                    val direction = when (type) {
                        Telephony.Sms.MESSAGE_TYPE_SENT,
                        Telephony.Sms.MESSAGE_TYPE_QUEUED -> MessageDirection.OUTBOUND
                        else -> MessageDirection.INBOUND
                    }
                    val sender = if (direction == MessageDirection.INBOUND) address else ""
                    val recipient = if (direction == MessageDirection.OUTBOUND) address else ""

                    val entity = MessageEntity(
                        id = "sys_$sysId",
                        deviceId = deviceId,
                        subscriptionId = 0,
                        type = MessageType.SMS.name,
                        direction = direction.name,
                        sender = sender,
                        recipient = recipient,
                        body = body,
                        state = MessageState.RECEIVED.name,
                        createdAt = date,
                        threadId = threadId
                    )
                    messageDao.upsertMessage(entity)
                    count++
                }
            }
        } catch (e: Exception) {
            // Permission not granted or query failed — silently ignore
        }
    }

    /**
     * Get all messages, ordered by most recent first.
     * Used by the API GET /messages endpoint.
     */
    fun getAllMessages(): Flow<List<Message>> {
        return messageDao.getAllMessages().map { entities ->
            entities.map { it.toMessageDomain() }
        }
    }

    fun getConversations(): Flow<List<Conversation>> {
        return messageDao.getConversations().map { tuples ->
            tuples.map { tuple ->
                Conversation(
                    threadId = tuple.threadId ?: "", contactAddress = tuple.contactAddress,
                    lastMessage = tuple.lastMessage, lastMessageAt = tuple.lastMessageAt,
                    unreadCount = tuple.unreadCount, isGroup = tuple.isGroup > 0
                )
            }
        }
    }

    fun getMessagesByThread(threadId: String): Flow<List<Message>> {
        return messageDao.getMessagesByThread(threadId).map { entities -> entities.map { it.toMessageDomain() } }
    }

    suspend fun markSeen(threadId: String) { messageDao.markThreadSeen(threadId) }

    suspend fun getMessage(id: String): Message? = messageDao.getMessage(id)?.toMessageDomain()

    fun generateThreadId(address: String): String = "thread_${CryptoUtils.hashApiKey(address).take(16)}"

    private fun smsResultCodeToError(resultCode: Int): String = when (resultCode) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic failure"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio is off"
        SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU"
        SmsManager.RESULT_ERROR_NO_SERVICE -> "No service"
        else -> "Error code: $resultCode"
    }
}

fun Message.toMessageEntity() = MessageEntity(
    id = id, deviceId = deviceId, subscriptionId = subscriptionId,
    type = type.name, direction = direction.name, sender = sender,
    recipient = recipient, body = body, state = state.name,
    createdAt = createdAt, sentAt = sentAt, deliveredAt = deliveredAt,
    readAt = readAt, errorCode = errorCode, errorMessage = errorMessage,
    threadId = threadId, seen = seen
)

fun MessageEntity.toMessageDomain() = Message(
    id = id, deviceId = deviceId, subscriptionId = subscriptionId,
    type = MessageType.valueOf(type), direction = MessageDirection.valueOf(direction),
    sender = sender, recipient = recipient, body = body,
    state = MessageState.valueOf(state), createdAt = createdAt, sentAt = sentAt,
    deliveredAt = deliveredAt, readAt = readAt, errorCode = errorCode,
    errorMessage = errorMessage, threadId = threadId, seen = seen
)
