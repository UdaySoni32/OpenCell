package io.opencell.platform.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import io.opencell.platform.di.PlatformEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for incoming SMS messages.
 * Registered in manifest with high priority (999).
 *
 * Uses Hilt EntryPoint to obtain [MessagingEngine] directly, since BroadcastReceiver
 * is instantiated by the system and cannot use @Inject.  Processing happens in a
 * background coroutine so the broadcast completes quickly.
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "OpenCellSmsRx"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                val subscriptionId = intent.getIntExtra("subscription", 0)

                if (messages.isNullOrEmpty()) {
                    Log.w(TAG, "SMS_RECEIVED_ACTION but no messages in intent")
                    return
                }

                // Obtain platform services via Hilt EntryPoint
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    PlatformEntryPoint::class.java
                )
                val messagingEngine = entryPoint.messagingEngine()
                val deviceEngine = entryPoint.deviceEngine()

                messages.forEach { smsMessage ->
                    val sender = smsMessage.displayOriginatingAddress
                        ?: smsMessage.originatingAddress ?: ""
                    val body = smsMessage.messageBody ?: ""
                    val timestamp = smsMessage.timestampMillis

                    Log.d(TAG, "SMS received from: $sender, body length: ${body.length}")

                    // Process the incoming SMS on a background thread
                    scope.launch {
                        try {
                            val deviceId = deviceEngine.getLocalDeviceId()
                            messagingEngine.onIncomingSms(
                                sender = sender,
                                body = body,
                                deviceId = deviceId,
                                timestamp = timestamp,
                                subscriptionId = subscriptionId
                            )
                            Log.d(TAG, "SMS from $sender processed successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to process SMS from $sender", e)
                        }
                    }
                }
            }

            Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION -> {
                Log.d(TAG, "WAP push received (MMS)")
                // MMS processing is handled by the system when OpenCell is the default SMS app
            }
        }
    }
}
