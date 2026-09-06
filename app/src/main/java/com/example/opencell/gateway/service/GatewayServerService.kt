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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

        startForeground(NOTIFICATION_ID, createNotification(port))

        serviceScope.launch {
            try {
                val app = application as OpenCellApplication
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
                    port = port
                ).apply { start() }

                _isServerRunning.value = true
                _serverPort.value = port
                ServerLogRepository.instance.log(ServerLogType.SYSTEM, "GatewayServerService started on port $port")
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

    private fun createNotification(port: Int): Notification {
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OpenCell Local Gateway")
            .setContentText("REST & WebSockets server active at http://127.0.0.1:$port")
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
