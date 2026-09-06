package com.example.opencell.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.opencell.OpenCellApplication
import com.example.opencell.domain.model.MessageRecord
import com.example.opencell.domain.model.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val sender = messages[0].originatingAddress ?: "Unknown"
            val body = messages.joinToString(separator = "") { it.messageBody ?: "" }
            val timestamp = messages[0].timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()

            val app = context.applicationContext as? OpenCellApplication ?: return
            val messageRepository = app.messageRepository

            val record = MessageRecord(
                address = sender,
                body = body,
                timestamp = timestamp,
                isIncoming = true,
                isRead = false,
                status = MessageStatus.RECEIVED
            )

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    messageRepository.insertMessage(record)
                    MessageEngine.instance?.onIncomingMessageReceived(record)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
