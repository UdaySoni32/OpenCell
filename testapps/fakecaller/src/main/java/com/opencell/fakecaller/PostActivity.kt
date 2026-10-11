package com.opencell.fakecaller

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle

/**
 * Posts (type=ringing|ongoing|missed) or cancels (type=clear) a WhatsApp-style call notification, then finishes.
 * Usage: adb shell am start -n com.whatsapp/com.opencell.fakecaller.PostActivity --es type missed --es name Riya
 */
class PostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("calls", "Calls", NotificationManager.IMPORTANCE_HIGH))
        val name = intent.getStringExtra("name") ?: "Riya"
        val type = intent.getStringExtra("type") ?: "missed"
        if (type == "clear") {
            nm.cancelAll()
            finish()
            return
        }
        val tap = PendingIntent.getActivity(this, 0, Intent(this, PostActivity::class.java).putExtra("type", "noop"), PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, "calls")
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(name)
            .setContentIntent(tap)
        when (type) {
            "ringing" -> builder.setContentText("Incoming voice call").setCategory(Notification.CATEGORY_CALL).setOngoing(true)
            "ongoing" -> builder.setContentText("Ongoing voice call").setCategory(Notification.CATEGORY_CALL).setOngoing(true)
            "missed" -> builder.setContentText("Missed voice call").setCategory(Notification.CATEGORY_MISSED_CALL)
            else -> {
                finish()
                return
            }
        }
        nm.notify(1, builder.build())
        finish()
    }
}
