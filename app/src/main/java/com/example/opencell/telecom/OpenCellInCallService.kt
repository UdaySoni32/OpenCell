package com.example.opencell.telecom

import android.content.pm.ServiceInfo
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import com.example.opencell.data.repository.ContactLookup
import com.example.opencell.domain.model.CallSession
import com.example.opencell.domain.model.CallState

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
        CallNotificationManager.createNotificationChannel(this)

        val handle = call.details?.handle?.schemeSpecificPart ?: "Unknown"
        val resolvedName = ContactLookup.resolveContactName(this, handle)
        val isRinging = call.state == Call.STATE_RINGING

        val session = CallSession(
            phoneNumber = handle,
            contactName = resolvedName,
            state = if (isRinging) CallState.RINGING else CallState.ACTIVE,
            isIncoming = isRinging
        )

        if (isRinging) {
            CallNotificationManager.showIncomingCallNotification(this, session)
        } else {
            CallNotificationManager.showActiveCallNotification(this, session)
        }
    }

    private fun stopForegroundNotification() {
        CallNotificationManager.cancelNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        @Volatile
        var activeServiceInstance: OpenCellInCallService? = null
            private set
    }
}
