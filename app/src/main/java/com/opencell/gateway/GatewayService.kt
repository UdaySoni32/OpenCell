package com.opencell.gateway

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.opencell.OpenCellApp
import com.opencell.R
import com.opencell.ui.MainActivity
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.application.ApplicationStarting
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

/** Foreground service hosting the local API on [PORT]. Reachable on LAN, USB (adb forward) and Tailscale addresses. */
class GatewayService : Service() {

    private var server: CIOApplicationEngine? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (server != null) return START_STICKY
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        val container = (application as OpenCellApp).container
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "dev"
        server = runCatching {
            val env = applicationEngineEnvironment {
                connector {
                    port = PORT
                    host = "127.0.0.1"
                }
            }
            // Installing the module from ApplicationStarting avoids Ktor 2's reflective module-by-name lookup,
            // which breaks under R8 in release builds.
            env.monitor.subscribe(ApplicationStarting) { app -> app.gatewayModule(AndroidGatewayBackend(container, version)) }
            embeddedServer(CIO, env).also { it.start(wait = false) }
        }.onFailure {
            Log.e(TAG, "gateway failed to start", it)
            stopSelf()
        }.getOrNull()
        _running.value = server != null
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        server = null
        _running.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification() = run {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Gateway", NotificationManager.IMPORTANCE_LOW))
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("OpenCell gateway is on")
            .setContentText("Port $PORT · ${localAddresses().joinToString().ifEmpty { "no network" }}")
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            .build()
    }

    companion object {
        const val PORT = 8787
        private const val TAG = "GatewayService"
        private const val CHANNEL = "gateway"
        private const val NOTIFICATION_ID = 3001

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context) {
            context.startForegroundService(Intent(context, GatewayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GatewayService::class.java))
        }

        /** IPv4 addresses computers can reach: Wi-Fi/LAN, hotspot, and Tailscale (100.x). */
        fun localAddresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress.orEmpty() }
                .filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())
    }
}
