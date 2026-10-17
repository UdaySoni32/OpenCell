package com.opencell.assistant

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import com.opencell.OpenCellApp
import com.opencell.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object ReminderScheduler {
    fun schedule(context: Context, reminder: AssistantReminder) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.triggerAtMillis,
            pendingIntent(context, reminder.id, reminder.title),
        )
    }

    fun cancel(context: Context, reminder: AssistantReminder) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, reminder.id, reminder.title))
    }

    private fun pendingIntent(context: Context, id: Long, title: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.toInt(),
        Intent(context, AssistantReminderReceiver::class.java)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_TITLE, title),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    const val EXTRA_ID = "reminder_id"
    const val EXTRA_TITLE = "reminder_title"
}

class AssistantReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1).takeIf { it >= 0 } ?: return
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty().ifBlank { "OpenCell reminder" }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Assistant reminders", NotificationManager.IMPORTANCE_HIGH))
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify(50_000 + id.toInt(), NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Reminder")
                .setContentText(title)
                .setAutoCancel(true)
                .build())
        }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                (context.applicationContext as OpenCellApp).container.database.assistantTaskDao().markReminderFired(id)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val CHANNEL = "assistant_reminders"
    }
}
