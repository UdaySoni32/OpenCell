package io.opencell.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives boot completed and package replaced broadcasts.
 * Ensures the API server and event engine restart after device reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // The WorkManager job scheduled by the Application class
                // will handle restarting the API server.
                // We use a broadcast because Application.onCreate may not
                // fire for BOOT_COMPLETED on all OEMs.
            }
        }
    }
}
