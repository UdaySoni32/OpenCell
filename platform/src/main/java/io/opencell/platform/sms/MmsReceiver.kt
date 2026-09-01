package io.opencell.platform.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Required by Android to be recognized as a default SMS app.
 * Handles WAP Push and MMS notifications.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // MMS processing logic would go here
    }
}
