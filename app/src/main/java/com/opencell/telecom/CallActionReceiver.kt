package com.opencell.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.opencell.OpenCellApp

/** Decline / hang up buttons on call notifications (Answer opens [InCallActivity] directly). */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val calls = (context.applicationContext as OpenCellApp).container.callManager
        val id = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        when (intent.action) {
            ACTION_DECLINE -> calls.reject(id)
            ACTION_HANGUP -> calls.hangup(id)
        }
    }

    companion object {
        const val ACTION_DECLINE = "com.opencell.action.DECLINE"
        const val ACTION_HANGUP = "com.opencell.action.HANGUP"
        const val EXTRA_CALL_ID = "call_id"
    }
}
