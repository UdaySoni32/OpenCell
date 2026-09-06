package com.example.opencell.messaging

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.opencell.OpenCellApplication
import com.example.opencell.domain.model.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsDeliveredReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        if (messageId == -1L) return

        val app = context.applicationContext as? OpenCellApplication ?: return
        val messageRepository = app.messageRepository

        val status = if (resultCode == Activity.RESULT_OK) {
            MessageStatus.DELIVERED
        } else {
            MessageStatus.FAILED
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                messageRepository.updateMessageStatus(messageId, status)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SMS_DELIVERED = "com.example.opencell.SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "com.example.opencell.MESSAGE_ID"
    }
}
