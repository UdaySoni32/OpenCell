package io.opencell.platform.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import io.opencell.core.database.dao.MessageDao
import io.opencell.core.model.MessageState
import io.opencell.platform.di.PlatformEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives delivery status for outgoing SMS messages.
 *
 * These receivers are registered programmatically in [MessagingEngine.sendSms] via PendingIntent.
 * The manifest also registers this class for the explicit action intents.
 *
 * SMS_SENT_ACTION → fired when the SMS has been accepted by the network.
 * SMS_DELIVERED_ACTION → fired when the SMS has been delivered to the recipient's handset.
 */
class SmsDeliveryReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SMS_SENT = "SMS_SENT_ACTION"
        const val ACTION_SMS_DELIVERED = "SMS_DELIVERED_ACTION"
        const val EXTRA_MESSAGE_ID = "message_id"
        private const val TAG = "SmsDeliveryRx"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: run {
            Log.w(TAG, "Received ${intent.action} without message_id extra")
            return
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            PlatformEntryPoint::class.java
        )
        val messagingEngine = entryPoint.messagingEngine()
        val deviceId = entryPoint.deviceEngine().getLocalDeviceIdSync()

        when (intent.action) {
            ACTION_SMS_SENT -> {
                val success = resultCode == Activity.RESULT_OK
                val newState = if (success) MessageState.SENT else MessageState.FAILED
                Log.d(TAG, "SMS_SENT for $messageId: success=$success, resultCode=$resultCode")

                scope.launch {
                    messagingEngine.onSmsSentStatus(
                        messageId = messageId,
                        success = success,
                        resultCode = resultCode,
                        deviceId = deviceId
                    )
                }
            }

            ACTION_SMS_DELIVERED -> {
                Log.d(TAG, "SMS_DELIVERED for $messageId")
                scope.launch {
                    messagingEngine.onSmsDeliveredStatus(
                        messageId = messageId,
                        deviceId = deviceId
                    )
                }
            }
        }
    }
}
