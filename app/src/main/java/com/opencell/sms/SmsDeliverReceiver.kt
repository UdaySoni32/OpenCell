package com.opencell.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.opencell.OpenCellApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Incoming SMS (only delivered to the default SMS app). Stores it and notifies. */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val address = parts.first().displayOriginatingAddress ?: return
        val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        val sentAt = parts.first().timestampMillis
        val container = (context.applicationContext as OpenCellApp).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val id = container.smsRepository.storeIncoming(address, body, sentAt)
                container.smsNotifier.notifyIncoming(address, body, container.smsRepository.threadIdFor(address))
                if (id != null) container.onIncomingSms(id, address, body)
            } catch (e: Exception) {
                Log.e("SmsDeliverReceiver", "failed to store SMS", e)
            } finally {
                pending.finish()
            }
        }
    }
}
