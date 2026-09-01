package io.opencell.platform.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Required by Android to be recognized as a default SMS app.
 * This service handles sending SMS/MMS messages in the background without a UI.
 */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
