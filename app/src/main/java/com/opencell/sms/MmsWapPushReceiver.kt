package com.opencell.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Required for the default SMS role.
 * shortcut: MMS download is not implemented; MMS notifications are only logged. Add MMS when users need group/picture messages.
 */
class MmsWapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("MmsWapPushReceiver", "MMS received; MMS is not supported yet")
    }
}
