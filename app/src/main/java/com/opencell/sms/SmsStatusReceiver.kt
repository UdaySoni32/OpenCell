package com.opencell.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage as PduMessage
import com.opencell.OpenCellApp

/** Sent / delivered results for messages queued by [SmsRepository.send]. */
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data ?: return
        val repo = (context.applicationContext as OpenCellApp).container.smsRepository
        val isLastPart = intent.getIntExtra(EXTRA_PART, 0) == intent.getIntExtra(EXTRA_TOTAL, 1) - 1
        when (intent.action) {
            ACTION_SENT -> when {
                resultCode != Activity.RESULT_OK -> repo.markType(uri, Telephony.Sms.MESSAGE_TYPE_FAILED)
                isLastPart -> repo.markType(uri, Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            ACTION_DELIVERED -> if (isLastPart) {
                val pdu = intent.getByteArrayExtra("pdu")
                val format = intent.getStringExtra("format")
                val status = pdu?.let { PduMessage.createFromPdu(it, format)?.status } ?: Telephony.Sms.STATUS_COMPLETE
                // TP-Status: 0x40 and above is a permanent failure; lower values mean delivered or still trying.
                repo.markStatus(uri, if (status >= 0x40) Telephony.Sms.STATUS_FAILED else Telephony.Sms.STATUS_COMPLETE)
            }
        }
    }

    companion object {
        const val ACTION_SENT = "com.opencell.sms.SENT"
        const val ACTION_DELIVERED = "com.opencell.sms.DELIVERED"
        const val EXTRA_PART = "part"
        const val EXTRA_TOTAL = "total"
    }
}
