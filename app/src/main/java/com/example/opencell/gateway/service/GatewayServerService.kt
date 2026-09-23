package com.example.opencell.gateway.service

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.opencell.MainActivity
import com.example.opencell.OpenCellApplication
import com.example.opencell.domain.model.ServerLogType
import com.example.opencell.gateway.logging.ServerLogRepository
import com.example.opencell.gateway.server.GatewayServer
import com.example.opencell.gateway.webhook.WebhookEngine
import com.example.opencell.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GatewayServerService : Service() {

    private var gatewayServer: GatewayServer? = null
    private var webhookEngine: WebhookEngine? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val port = intent?.getIntExtra(EXTRA_PORT, 8080) ?: 8080

        serviceScope.launch {
            try {
                val app = application as OpenCellApplication
                val allowRemote = try {
                    app.developerPreferencesRepository.allowRemoteAccess.first()
                } catch (_: Exception) {
                    true
                }

                val host = if (allowRemote) "0.0.0.0" else "127.0.0.1"
                val localIp = NetworkUtils.getLocalIpAddress(app)
                _localIpAddress.value = localIp

                startForeground(NOTIFICATION_ID, createNotification(port, localIp, allowRemote))

                webhookEngine = WebhookEngine(
                    developerPreferencesRepository = app.developerPreferencesRepository
                ).apply { start() }

                gatewayServer = GatewayServer(
                    context = app,
                    developerPreferencesRepository = app.developerPreferencesRepository,
                    callRepository = app.callRepository,
                    messageRepository = app.messageRepository,
                    callEngine = app.callEngine,
                    messageEngine = app.messageEngine,
                    port = port,
                    host = host
                ).apply { start() }

                _isServerRunning.value = true
                _serverPort.value = port
                _serverHost.value = host

                val boundMsg = if (allowRemote) "0.0.0.0 (LAN & Loopback)" else "127.0.0.1 (Loopback only)"
                ServerLogRepository.instance.log(
                    ServerLogType.SYSTEM,
                    "GatewayServerService started on port $port bound to $boundMsg" +
                            if (localIp != null) " [LAN IP: $localIp]" else ""
                )
            } catch (e: Exception) {
                ServerLogRepository.instance.log(ServerLogType.SYSTEM, "GatewayServerService failed to start: ${e.message}")
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {

        serviceScope.launch {
            try {
                webhookEngine?.stop()
                gatewayServer?.stop()
            } catch (e: Exception) {
                ServerLogRepository.instance.log(ServerLogType.SYSTEM, "Error stopping GatewayServerService: ${e.message}")
            } finally {
                _isServerRunning.value = false
            }
        }

        ServerLogRepository.instance.log(ServerLogType.SYSTEM, "GatewayServerService destroyed")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OpenCell Local Gateway Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground Service hosting embedded REST & WebSocket local API gateway"
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(port: Int, localIp: String?, allowRemote: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, GatewayServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val urlText = when {
            allowRemote && localIp != null -> "LAN: http://$localIp:$port"
            allowRemote -> "Bound to 0.0.0.0:$port (LAN Access Active)"
            else -> "http://127.0.0.1:$port (Local Loopback Only)"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenCell Gateway API Active")
            .setContentText(urlText)
            .setSmallIcon(R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_menu_close_clear_cancel, "Stop Gateway", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "opencell_gateway_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.example.opencell.gateway.STOP_SERVICE"
        const val EXTRA_PORT = "extra_port"

        private val _isServerRunning = MutableStateFlow(false)
        val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

        private val _serverPort = MutableStateFlow(8080)
        val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

        private val _serverHost = MutableStateFlow("0.0.0.0")
        val serverHost: StateFlow<String> = _serverHost.asStateFlow()

        private val _localIpAddress = MutableStateFlow<String?>(null)
        val localIpAddress: StateFlow<String?> = _localIpAddress.asStateFlow()

        fun startService(context: Context, port: Int = 8080) {
            val intent = Intent(context, GatewayServerService::class.java).apply {
                putExtra(EXTRA_PORT, port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, GatewayServerService::class.java)
            context.stopService(intent)
            _isServerRunning.value = false
        }
    }
}
