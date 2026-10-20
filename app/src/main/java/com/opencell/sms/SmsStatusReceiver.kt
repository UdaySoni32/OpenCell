package com.opencell.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import android.telephony.SmsMessage as PduMessage
import com.opencell.OpenCellApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Sent / delivered results for messages queued by [SmsRepository.send]. */
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data?.buildUpon()?.fragment(null)?.build() ?: return
        if (intent.action != ACTION_SENT && intent.action != ACTION_DELIVERED) return
        val repo = (context.applicationContext as OpenCellApp).container.smsRepository
        val isDelivery = intent.action == ACTION_DELIVERED
        val part = intent.getIntExtra(EXTRA_PART, 0)
        val total = intent.getIntExtra(EXTRA_TOTAL, 1)
        val result = if (isDelivery) {
            val status = runCatching {
                intent.getByteArrayExtra("pdu")?.let { PduMessage.createFromPdu(it, intent.getStringExtra("format"))?.status }
            }.getOrNull()
            smsDeliveryResult(resultCode == Activity.RESULT_OK, status)
        } else if (resultCode == Activity.RESULT_OK) 'S' else 'F'
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                synchronized(lock) {
                    val prefs = preferences(context)
                    val key = uri.toString()
                    // Missing means completed/failed (or not queued by us): never resurrect it.
                    val state = prefs.getString(key, null)?.let(MultipartSmsState::decode) ?: return@synchronized
                    if (total != state.sent.length || part !in state.sent.indices) return@synchronized
                    val next = state.record(part, isDelivery, result)
                    check(prefs.edit().putString(key, next.encode()).commit())
                    when {
                        next.failed -> {
                            repo.markType(uri, Telephony.Sms.MESSAGE_TYPE_FAILED)
                            repo.markStatus(uri, Telephony.Sms.STATUS_FAILED)
                        }
                        next.sentComplete -> {
                            repo.markType(uri, Telephony.Sms.MESSAGE_TYPE_SENT)
                            if (next.deliveredComplete) repo.markStatus(uri, Telephony.Sms.STATUS_COMPLETE)
                        }
                    }
                    if (next.complete) prefs.edit().remove(key).commit()
                }
            } catch (e: Exception) {
                Log.w("SmsStatusReceiver", "Unable to persist SMS status", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val lock = Any()
        private fun preferences(context: Context) = context.getSharedPreferences("sms_status", Context.MODE_PRIVATE)

        internal fun begin(context: Context, uri: Uri, total: Int) = synchronized(lock) {
            check(preferences(context).edit().putString(uri.toString(), MultipartSmsState.pending(total).encode()).commit())
        }

        internal fun forget(context: Context, uri: Uri) = synchronized(lock) {
            preferences(context).edit().remove(uri.toString()).commit()
        }

        const val ACTION_SENT = "com.opencell.sms.SENT"
        const val ACTION_DELIVERED = "com.opencell.sms.DELIVERED"
        const val EXTRA_PART = "part"
        const val EXTRA_TOTAL = "total"
    }
}
