package com.opencell.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.opencell.R
import com.opencell.notifications.hasNotificationPermission

class AssistantNotifications(private val context: Context) : AssistantNotifier {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var nextId = 40_000

    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Assistant", NotificationManager.IMPORTANCE_DEFAULT))
    }

    override fun info(title: String, text: String) {
        if (!context.hasNotificationPermission()) return
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        manager.notify(nextId++, n)
    }

    private companion object {
        const val CHANNEL = "assistant"
    }
}
