package com.opencell.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.opencell.OpenCellApp
import com.opencell.ui.settings.setupStatus
import com.opencell.ui.theme.OpenCellTheme

class MainActivity : ComponentActivity() {

    /** Deep link derived from the launching intent (dial a number, open a thread…), consumed by the nav graph. */
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute.value = routeFor(intent)
        val start = if (setupStatus(this).complete) Routes.CALLS else Routes.SETUP
        val container = (application as OpenCellApp).container
        setContent {
            OpenCellTheme {
                OpenCellNavHost(
                    startRoute = start,
                    pendingRoute = pendingRoute.value,
                    onRouteConsumed = { pendingRoute.value = null },
                    placeCall = container.callPlacer::placeCall,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingRoute.value = routeFor(intent)
    }

    private fun routeFor(intent: Intent?): String? {
        intent ?: return null
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1)
        if (threadId >= 0) return Routes.conversation(threadId, intent.getStringExtra(EXTRA_ADDRESS).orEmpty())
        val data: Uri = intent.data ?: return null
        return when (data.scheme) {
            "tel" -> Routes.dialer(data.schemeSpecificPart)
            "sms", "smsto", "mms", "mmsto" -> Routes.conversation(null, data.schemeSpecificPart.substringBefore('?'))
            else -> null
        }
    }

    companion object {
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_ADDRESS = "address"
    }
}
