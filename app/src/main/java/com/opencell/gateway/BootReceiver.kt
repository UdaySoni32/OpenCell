package com.opencell.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.opencell.OpenCellApp
import com.opencell.assistant.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Restores reminders after reboot, and restarts the gateway if the user left it on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as OpenCellApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                try {
                    ReminderScheduler.restorePending(context, container.database.assistantTaskDao())
                } catch (e: Exception) {
                    Log.w("BootReceiver", "Unable to restore reminders", e)
                }
                try {
                    if (container.settings.isGatewayEnabled()) GatewayService.start(context)
                } catch (e: Exception) {
                    Log.w("BootReceiver", "Unable to restart gateway", e)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
