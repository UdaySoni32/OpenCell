package io.opencell.platform.telecom

import android.net.Uri
import android.telecom.*
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import io.opencell.core.crypto.CryptoUtils
import io.opencell.platform.di.PlatformEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * ConnectionService for OpenCell phone role.
 *
 * When OpenCell is set as the default dialer, Android routes all telephony connections through here.
 * Each Connection object maps to a real call — we update [CallEngine] to keep UI and API in sync.
 */
class OpenCellConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "OpenCellConnSvc"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val callEngine: CallEngine by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            PlatformEntryPoint::class.java
        ).callEngine()
    }

    private val deviceEngine: io.opencell.platform.devices.DeviceEngine by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            PlatformEntryPoint::class.java
        ).deviceEngine()
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val phoneNumber = request?.address?.schemeSpecificPart ?: ""
        Log.d(TAG, "onCreateOutgoingConnection: $phoneNumber")

        val callId = CryptoUtils.generateId("call")

        val connection = createOpenCellConnection(callId, phoneNumber, isIncoming = false)
        connection.setAddress(Uri.parse("tel:$phoneNumber"), TelecomManager.PRESENTATION_ALLOWED)
        connection.setInitializing()
        connection.setDialing()

        // We intentionally do NOT call callEngine.makeCall() here since the outgoing call
        // was either initiated by the user via DialerScreen (which already called makeCall)
        // or by the API (same). This just mirrors the connection state back.

        return connection
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val phoneNumber = request?.address?.schemeSpecificPart ?: ""
        val displayName = request?.extras?.getString(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS) ?: ""
        Log.d(TAG, "onCreateIncomingConnection: $phoneNumber")

        val callId = CryptoUtils.generateId("call")

        val connection = createOpenCellConnection(callId, phoneNumber, isIncoming = true)
        connection.setAddress(Uri.parse("tel:$phoneNumber"), TelecomManager.PRESENTATION_ALLOWED)
        connection.setRinging()

        // Notify CallEngine of the incoming call
        serviceScope.launch {
            val deviceId = deviceEngine.getLocalDeviceId()
            callEngine.onIncomingCall(
                callId = callId,
                phoneNumber = phoneNumber,
                displayName = displayName.ifBlank { null },
                deviceId = deviceId
            )
        }

        return connection
    }

    private fun createOpenCellConnection(
        callId: String,
        phoneNumber: String,
        isIncoming: Boolean
    ): Connection {
        return object : Connection() {
            override fun onAnswer() {
                setActive()
                serviceScope.launch { callEngine.answerCall(callId) }
            }

            override fun onReject() {
                setDisconnected(DisconnectCause(DisconnectCause.REJECTED, "Rejected"))
                destroy()
                serviceScope.launch { callEngine.rejectCall(callId) }
            }

            override fun onHold() {
                setOnHold()
                serviceScope.launch { callEngine.holdCall(callId) }
            }

            override fun onUnhold() {
                setActive()
                serviceScope.launch { callEngine.resumeCall(callId) }
            }

            override fun onDisconnect() {
                setDisconnected(DisconnectCause(DisconnectCause.LOCAL, "User ended call"))
                destroy()
                serviceScope.launch { callEngine.hangupCall(callId) }
            }

            override fun onCallAudioStateChanged(state: CallAudioState?) {
                // Audio routing state changes (speaker, earpiece, bluetooth) can be tracked here
                Log.d(TAG, "Audio state changed: ${state?.route}")
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
