package com.opencell.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.opencell.OpenCellApp
import kotlinx.coroutines.runBlocking

/** Restarts the gateway after reboot if the user left it on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = (context.applicationContext as OpenCellApp).container.settings
        // A single DataStore read; fine to block briefly inside a boot broadcast.
        if (runBlocking { settings.isGatewayEnabled() }) GatewayService.start(context)
    }
}
