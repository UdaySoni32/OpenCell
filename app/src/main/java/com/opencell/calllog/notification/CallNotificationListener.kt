package com.opencell.calllog.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.opencell.OpenCellApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Watches other apps' notifications for calls and records them in the unified call log. */
class CallNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ongoing = ConcurrentHashMap<String, CallEvent>()

    private val container get() = (application as OpenCellApp).container

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val event = CallNotificationParser.parse(sbn.toSnapshot()) ?: return
        sbn.notification.contentIntent?.let {
            container.callNotificationCache.put(sbn.key, sbn.packageName, event.peerName, it)
        }
        if (event.type == CallEventType.ONGOING) ongoing[sbn.key] = event
        scope.launch {
            runCatching { container.appCallRecorder.onEvent(event) }
                .onFailure { Log.e(TAG, "record call event failed", it) }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        container.callNotificationCache.remove(sbn.key)
        val ended = ongoing.remove(sbn.key) ?: return
        scope.launch {
            runCatching { container.appCallRecorder.onCallEnded(ended.packageName, ended.peerName, System.currentTimeMillis()) }
                .onFailure { Log.e(TAG, "record call end failed", it) }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun StatusBarNotification.toSnapshot(): NotificationSnapshot {
        val extras = notification.extras
        return NotificationSnapshot(
            key = key,
            packageName = packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString(),
            category = notification.category,
            isOngoing = isOngoing,
            postTimeMillis = postTime,
        )
    }

    private companion object {
        const val TAG = "CallNotifListener"
    }
}
