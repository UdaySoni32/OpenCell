package com.opencell.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.opencell.OpenCellApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** "Reply with message" when declining a call; required for the default SMS role. */
class RespondViaMessageService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.data?.schemeSpecificPart
        val body = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (address.isNullOrBlank() || body.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        scope.launch {
            (application as OpenCellApp).container.smsRepository.send(address, body)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
