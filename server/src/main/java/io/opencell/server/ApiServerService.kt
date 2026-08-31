package io.opencell.server

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.net.Inet4Address

class ApiServerService : Service() {

    companion object {
        private const val TAG = "OpenCellApiSvc"
        const val CHANNEL_ID = "opencell_api_server"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "io.opencell.server.START"
        const val ACTION_STOP = "io.opencell.server.STOP"

        @Volatile
        var serverInstance: io.opencell.server.api.ApiServer? = null

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ApiServerService::class.java).apply { action = ACTION_START })
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ApiServerService::class.java).apply { action = ACTION_STOP })
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopServer()
        }
        return START_STICKY
    }

    private fun startServer() {
        startForeground(NOTIFICATION_ID, createNotification("API Server starting..."))
        val server = serverInstance
        if (server == null) {
            Log.e(TAG, "Server instance not set — check that OpenCellApp.onCreate() ran and ServerModule is configured")
            updateNotification("API Server: not configured (serverInstance is null)")
            return
        }
        Log.d(TAG, "Server instance found, starting Ktor on 0.0.0.0:8900...")
        serviceScope.launch {
            try {
                server.start()
                val ip = getLocalIpAddress() ?: "0.0.0.0"
                val msg = "API Server running on $ip:8900"
                updateNotification(msg)
                Log.i(TAG, msg)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start API server: ${e.message}", e)
                updateNotification("API Server failed: ${e.message}")
                // Don't stopSelf() — keep the foreground service alive so the user can see the error
            }
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val linkProperties: LinkProperties = cm.getLinkProperties(cm.activeNetwork) ?: return null
            for (address in linkProperties.linkAddresses) {
                val inetAddress = address.address
                if (inetAddress is Inet4Address && !inetAddress.isLoopbackAddress) {
                    return inetAddress.hostAddress
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP", e)
        }
        return null
    }

    private fun stopServer() {
        serviceScope.launch {
            serverInstance?.stop()
            updateNotification("API Server stopped")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "OpenCell API Server", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, packageManager.getLaunchIntentForPackage(packageName), PendingIntent.FLAG_IMMUTABLE
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID).setContentTitle("OpenCell API").setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_manage).setContentIntent(pendingIntent).setOngoing(true).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setContentTitle("OpenCell API").setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_manage).setContentIntent(pendingIntent).setOngoing(true).build()
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, createNotification(text))
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
