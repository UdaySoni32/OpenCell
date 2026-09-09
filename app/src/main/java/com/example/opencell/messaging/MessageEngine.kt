package com.example.opencell.messaging

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.opencell.OpenCellApplication
import com.example.opencell.data.repository.MessageRepository
import com.example.opencell.domain.model.MessageRecord
import com.example.opencell.domain.model.MessageStatus
import com.example.opencell.gateway.event.EventEngine
import com.example.opencell.telecom.RingtoneVibrationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class MessageEngine(
    private val context: Context,
    private val smsAdapter: SmsAdapter,
    private val messageRepository: MessageRepository,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.Default + Job())
) {

    private val _isSmsCapable = MutableStateFlow(smsAdapter.isSmsCapable())
    val isSmsCapable: StateFlow<Boolean> = _isSmsCapable.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        instance = this
    }

    fun refreshSmsStatus() {
        _isSmsCapable.value = smsAdapter.isSmsCapable()
    }

    fun sendMessage(
        destination: String,
        body: String,
        contactName: String? = null,
        allowSimulationFallback: Boolean = true
    ) {
        val sanitizedRecipient = destination.trim()
        val sanitizedBody = body.trim()

        if (sanitizedRecipient.isBlank() || sanitizedBody.isBlank()) {
            _statusMessage.value = "Cannot send message: Recipient or Body is empty"
            return
        }

        refreshSmsStatus()
        val hasPermissions = smsAdapter.hasSmsPermissions()
        val isCapable = smsAdapter.isSmsCapable()

        externalScope.launch {
            val initialRecord = MessageRecord(
                address = sanitizedRecipient,
                contactName = contactName,
                body = sanitizedBody,
                timestamp = System.currentTimeMillis(),
                isIncoming = false,
                isRead = true,
                status = MessageStatus.SENDING
            )
            val messageId = messageRepository.insertMessage(initialRecord)

            EventEngine.instance.emitMessageCreated(
                messageId = messageId.toString(),
                address = sanitizedRecipient,
                body = sanitizedBody,
                isIncoming = false
            )

            if (isCapable && hasPermissions) {
                val sentSuccessfully = try {
                    val sentIntent = PendingIntent.getBroadcast(
                        context,
                        messageId.toInt(),
                        Intent(SmsSentReceiver.ACTION_SMS_SENT).apply {
                            putExtra(SmsSentReceiver.EXTRA_MESSAGE_ID, messageId)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val deliveredIntent = PendingIntent.getBroadcast(
                        context,
                        messageId.toInt(),
                        Intent(SmsDeliveredReceiver.ACTION_SMS_DELIVERED).apply {
                            putExtra(SmsDeliveredReceiver.EXTRA_MESSAGE_ID, messageId)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    smsAdapter.sendSms(
                        destination = sanitizedRecipient,
                        text = sanitizedBody,
                        sentIntent = sentIntent,
                        deliveryIntent = deliveredIntent
                    )
                } catch (_: Exception) {
                    false
                }

                if (sentSuccessfully) {
                    _statusMessage.value = "SMS dispatch submitted for $sanitizedRecipient"
                    return@launch
                }
            }

            if (!allowSimulationFallback) {
                val errorMsg = when {
                    !isCapable -> "Hardware SMS unavailable on device"
                    !hasPermissions -> "SMS permissions (SEND_SMS/RECEIVE_SMS) not granted"
                    else -> "SMS dispatch failed"
                }
                _statusMessage.value = errorMsg
                messageRepository.updateMessageStatus(messageId, MessageStatus.FAILED)
                EventEngine.instance.emitMessageStateChanged(
                    messageId = messageId.toString(),
                    status = "FAILED"
                )
                return@launch
            }

            // Simulation Fallback for testing/emulators without cellular network
            _statusMessage.value = "Simulated SMS sent to $sanitizedRecipient (no cellular service)"
            delay(1000)
            messageRepository.updateMessageStatus(messageId, MessageStatus.SENT)
            EventEngine.instance.emitMessageStateChanged(
                messageId = messageId.toString(),
                status = "SENT"
            )
            delay(1500)
            messageRepository.updateMessageStatus(messageId, MessageStatus.DELIVERED)
            EventEngine.instance.emitMessageStateChanged(
                messageId = messageId.toString(),
                status = "DELIVERED"
            )
        }
    }

    internal fun onIncomingMessageReceived(message: MessageRecord) {
        _statusMessage.value = "Incoming SMS received from ${message.address}"
        EventEngine.instance.emitMessageCreated(
            messageId = message.id.toString(),
            address = message.address,
            body = message.body,
            isIncoming = true
        )

        val appContext = OpenCellApplication.instanceOrNull ?: context.applicationContext
        if (appContext != null) {
            MessageNotificationManager.showIncomingMessageNotification(appContext, message)

            val app = appContext as? OpenCellApplication
            if (app?.isAppInForeground == true) {
                RingtoneVibrationManager.playSmsSoundAndVibration(appContext)
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    companion object {
        @Volatile
        private var instanceRef: WeakReference<MessageEngine>? = null

        var instance: MessageEngine?
            get() = instanceRef?.get()
            set(value) {
                instanceRef = if (value != null) WeakReference(value) else null
            }
    }
}
