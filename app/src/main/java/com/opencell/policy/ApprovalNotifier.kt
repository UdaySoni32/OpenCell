package com.opencell.policy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.opencell.OpenCellApp
import com.opencell.R
import com.opencell.notifications.hasNotificationPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ApprovalNotifier(private val context: Context) : ApprovalPrompter {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Approvals", NotificationManager.IMPORTANCE_HIGH))
    }

    override fun show(approval: ApprovalEntity) {
        if (!context.hasNotificationPermission()) return
        val what = when (approval.action) {
            ActionType.SEND_SMS -> "Send SMS to ${approval.target}"
            ActionType.PLACE_CALL -> "Call ${approval.target}"
            else -> "${approval.action.label} ${approval.target}"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${approval.actorLabel} wants to: $what")
            .setContentText(approval.body ?: "Tap Approve to allow")
            .setStyle(NotificationCompat.BigTextStyle().bigText(approval.body ?: what))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .addAction(0, "Deny", decisionIntent(approval.id, approve = false))
            .addAction(0, "Approve", decisionIntent(approval.id, approve = true))
            .build()
        manager.notify(notificationId(approval.id), notification)
    }

    override fun dismiss(approvalId: Long) {
        manager.cancel(notificationId(approvalId))
    }

    private fun decisionIntent(id: Long, approve: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context,
        (id * 2 + if (approve) 1 else 0).toInt(),
        Intent(context, ApprovalReceiver::class.java)
            .putExtra(ApprovalReceiver.EXTRA_ID, id)
            .putExtra(ApprovalReceiver.EXTRA_APPROVE, approve),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notificationId(approvalId: Long) = 20_000 + approvalId.toInt()

    private companion object {
        const val CHANNEL = "approvals"
    }
}

/** Approve / Deny buttons. The user tapping them is the permission the policy asked for. */
class ApprovalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1).takeIf { it >= 0 } ?: return
        val approve = intent.getBooleanExtra(EXTRA_APPROVE, false)
        val executor = (context.applicationContext as OpenCellApp).container.actionExecutor
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                executor.decide(id, approve, decidedBy = "you", decidingActor = Actor.User)
            } catch (e: Exception) {
                Log.e("ApprovalReceiver", "decision failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ID = "approval_id"
        const val EXTRA_APPROVE = "approve"
    }
}
