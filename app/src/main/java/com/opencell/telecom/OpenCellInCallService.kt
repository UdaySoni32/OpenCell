package com.opencell.telecom

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import com.opencell.OpenCellApp
import com.opencell.calllog.CallKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Bound by Telecom while we are the default dialer. SIM calls go to [CallManager] and our UI;
 * self-managed calls (WhatsApp etc. on some phones) are only logged into the unified call list.
 */
class OpenCellInCallService : InCallService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val container get() = (application as OpenCellApp).container
    private val selfManaged = ConcurrentHashMap<Call, SelfManagedInfo>()

    private data class SelfManagedInfo(
        val packageName: String,
        val peerName: String?,
        val isOutgoing: Boolean,
        val startedAt: Long,
        @Volatile var answeredAt: Long?,
    )

    override fun onCreate() {
        super.onCreate()
        container.callManager.attach(this)
        scope.launch { container.callManager.activeCalls.collect(::renderNotification) }
    }

    override fun onCallAdded(call: Call) {
        if (CallManager.isSelfManaged(call)) {
            trackSelfManaged(call)
            return
        }
        container.callManager.add(call)
        if (CallManager.stateOf(call) != Call.STATE_RINGING) showInCallUi()
    }

    override fun onCallRemoved(call: Call) {
        selfManaged.remove(call)?.let { recordSelfManaged(it) } ?: container.callManager.remove(call)
    }

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        container.callManager.onAudioStateChanged(audioState)
    }

    override fun onDestroy() {
        container.callManager.detach()
        getSystemService(NotificationManager::class.java).cancel(IncomingCallNotifier.NOTIFICATION_ID)
        scope.cancel()
        super.onDestroy()
    }

    // CallStyle notifications must come from a foreground service, so the service is foreground while any call exists.
    private fun renderNotification(calls: List<ActiveCall>) {
        val ringing = calls.firstOrNull { it.state == CallState.RINGING }
        val current = calls.firstOrNull { it.state != CallState.DISCONNECTED }
        val notification = when {
            ringing != null -> container.incomingCallNotifier.incoming(ringing)
            current != null -> container.incomingCallNotifier.ongoing(current)
            else -> null
        }
        runCatching {
            if (notification != null) {
                startForeground(IncomingCallNotifier.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
            } else {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }.onFailure { Log.e("InCallService", "call notification failed", it) }
    }

    private fun showInCallUi() {
        startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun trackSelfManaged(call: Call) {
        val details = call.details
        val pkg = details.accountHandle?.componentName?.packageName ?: return
        val info = SelfManagedInfo(
            packageName = pkg,
            peerName = details.callerDisplayName?.takeIf { it.isNotBlank() } ?: details.handle?.schemeSpecificPart,
            isOutgoing = details.callDirection == Call.Details.DIRECTION_OUTGOING,
            startedAt = System.currentTimeMillis(),
            answeredAt = null,
        )
        selfManaged[call] = info
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_ACTIVE && info.answeredAt == null) info.answeredAt = System.currentTimeMillis()
            }
        })
    }

    private fun recordSelfManaged(info: SelfManagedInfo) {
        val answeredAt = info.answeredAt
        val kind = when {
            info.isOutgoing -> CallKind.OUTGOING
            answeredAt != null -> CallKind.INCOMING
            else -> CallKind.MISSED
        }
        val duration = answeredAt?.let { (System.currentTimeMillis() - it) / 1000 } ?: 0
        scope.launch(Dispatchers.IO) {
            runCatching { container.appCallRecorder.onSelfManagedCall(info.packageName, info.peerName, kind, info.startedAt, duration) }
                .onFailure { Log.e("InCallService", "self-managed log failed", it) }
        }
    }
}
