package io.opencell.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import io.opencell.platform.telecom.OpenCellConnectionService
import io.opencell.server.ApiServerService
import io.opencell.server.api.ApiServer
import io.opencell.server.auth.AuthenticationService
import io.opencell.platform.devices.DeviceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class OpenCellApp : Application() {

    @Inject
    lateinit var apiServer: ApiServer

    @Inject
    lateinit var deviceEngine: DeviceEngine

    @Inject
    lateinit var authService: AuthenticationService

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Register PhoneAccount so Android shows OpenCell in the default phone app picker
        registerPhoneAccount()

        // Set static server reference for the foreground service
        ApiServerService.serverInstance = apiServer

        // Initialize device identity and create default API key
        appScope.launch {
            try {
                deviceEngine.getOrCreateLocalDevice()
                authService.createDefaultApiKeyIfNeeded()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize device or create API key", e)
            }
        }

        // Start API server as a foreground service
        ApiServerService.start(this)
    }

    override fun onTerminate() {
        ApiServerService.stop(this)
        super.onTerminate()
    }

    /**
     * Register a PhoneAccount with the Telecom framework.
     * Without this, OpenCell will NOT appear in the system's default phone app selector.
     * The PhoneAccount points to our ConnectionService so the framework routes calls through it.
     */
    private fun registerPhoneAccount() {
        val telecomManager = getSystemService(TELECOM_SERVICE) as? TelecomManager ?: return

        val componentName = ComponentName(this, OpenCellConnectionService::class.java)
        val phoneAccountHandle = PhoneAccountHandle(componentName, "opencell_account")

        val phoneAccount = PhoneAccount.builder(phoneAccountHandle, "OpenCell")
            .setCapabilities(
                PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS or
                PhoneAccount.CAPABILITY_CALL_PROVIDER
            )
            .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
            .setHighlightColor(0xFF4CAF50.toInt()) // Green accent
            .build()

        telecomManager.registerPhoneAccount(phoneAccount)
        Log.i(TAG, "PhoneAccount registered: $phoneAccountHandle")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val incomingCallChannel = NotificationChannel(
                INCOMING_CALL_CHANNEL, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming phone calls"
                enableVibration(true)
            }

            val smsChannel = NotificationChannel(
                SMS_CHANNEL, "Messages", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifications for incoming SMS/MMS messages" }

            val apiChannel = NotificationChannel(
                ApiServerService.CHANNEL_ID, "API Server", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps the local API server running" }

            manager.createNotificationChannels(listOf(incomingCallChannel, smsChannel, apiChannel))
        }
    }

    companion object {
        private const val TAG = "OpenCellApp"
        const val INCOMING_CALL_CHANNEL = "opencell_incoming_call"
        const val SMS_CHANNEL = "opencell_sms"
    }
}
