package com.example.opencell.telecom

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import com.example.opencell.ui.incall.InCallActivity

class OpenCellInCallService : InCallService() {

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            CallEngine.instance?.onSystemCallStateChanged(call, state)
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        activeServiceInstance = this
        call.registerCallback(callCallback)
        CallEngine.instance?.onSystemCallAdded(call, this)

        showInCallNotificationAndLaunchUi(call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        CallEngine.instance?.onSystemCallRemoved(call)
        if (activeServiceInstance == this) {
            activeServiceInstance = null
        }
        stopForegroundNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeServiceInstance == this) {
            activeServiceInstance = null
        }
        stopForegroundNotification()
    }

    private fun showInCallNotificationAndLaunchUi(call: Call) {
        createNotificationChannel()

        val activityIntent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val number = call.details?.handle?.schemeSpecificPart ?: "Unknown"
        @Suppress("DEPRECATION")
        val isRinging = call.state == Call.STATE_RINGING

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_menu_call)
            .setContentTitle(if (isRinging) "Incoming Call" else "Active Call")
            .setContentText(number)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        try {
            startActivity(activityIntent)
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "In-Call Services",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "OpenCell Active & Incoming Call Service Channel"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        const val CHANNEL_ID = "opencell_incall_channel"
        const val NOTIFICATION_ID = 2001

        @Volatile
        var activeServiceInstance: OpenCellInCallService? = null
            private set
    }
}
