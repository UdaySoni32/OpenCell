package com.opencell.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.opencell.R

/** Ringing (full-screen) and ongoing call notifications. */
class IncomingCallNotifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Ongoing calls", NotificationManager.IMPORTANCE_LOW),
        )
    }

    fun incoming(call: ActiveCall): Notification {
        val person = Person.Builder().setName(call.displayName).setImportant(true).build()
        return NotificationCompat.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(call.displayName)
            .setContentText("Incoming call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(inCallIntent(), true)
            .setContentIntent(inCallIntent())
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    person,
                    actionIntent(CallActionReceiver.ACTION_DECLINE, call.id),
                    answerIntent(call.id),
                ),
            )
            .build()
    }

    fun ongoing(call: ActiveCall): Notification {
        val person = Person.Builder().setName(call.displayName).build()
        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(call.displayName)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setUsesChronometer(call.connectedAtMillis != null)
            .setWhen(call.connectedAtMillis ?: System.currentTimeMillis())
            .setContentIntent(inCallIntent())
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, actionIntent(CallActionReceiver.ACTION_HANGUP, call.id)))
            .build()
    }

    private fun inCallIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // Android 12+ forbids starting activities from a notification's broadcast ("trampoline"), so Answer opens the UI directly.
    private fun answerIntent(callId: String): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, InCallActivity::class.java)
            .setAction(InCallActivity.ACTION_ANSWER)
            .putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionIntent(action: String, callId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, CallActionReceiver::class.java).setAction(action).putExtra(CallActionReceiver.EXTRA_CALL_ID, callId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val NOTIFICATION_ID = 1001
        private const val CHANNEL_INCOMING = "incoming_calls"
        private const val CHANNEL_ONGOING = "ongoing_calls"
    }
}
