package com.example.opencell.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ANSWER -> {
                CallEngine.instance?.answerCall()
            }
            ACTION_DECLINE -> {
                CallEngine.instance?.rejectCall()
            }
            ACTION_HANGUP -> {
                CallEngine.instance?.hangupCall()
            }
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.example.opencell.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE = "com.example.opencell.ACTION_DECLINE_CALL"
        const val ACTION_HANGUP = "com.example.opencell.ACTION_HANGUP_CALL"
    }
}
