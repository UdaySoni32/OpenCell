package io.opencell.platform.telecom

import android.os.IBinder
import android.telecom.Call
import android.telecom.InCallService
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
 * InCallService for OpenCell.
 *
 * This service is bound by the Android Telecom framework when OpenCell is the default phone app.
 * It bridges the Telecom framework's [Call] objects to our [CallEngine], so that both UI-initiated
 * calls and API-initiated calls share the same state machine.
 *
 * We use Hilt EntryPoint injection because InCallService is instantiated by the system, not Hilt.
 */
class OpenCellInCallService : InCallService() {

    companion object {
        private const val TAG = "OpenCellInCallSvc"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val callEngine: CallEngine by lazy {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            PlatformEntryPoint::class.java
        )
        entryPoint.callEngine()
    }

    private val deviceEngine: io.opencell.platform.devices.DeviceEngine by lazy {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            PlatformEntryPoint::class.java
        )
        entryPoint.deviceEngine()
    }

    // Map from Telecom Call object hashCode → our internal call ID
    private val telecomCallToId = mutableMapOf<Int, String>()

    override fun onBind(intent: android.content.Intent?): IBinder? = super.onBind(intent)

    override fun onCallAdded(telecomCall: Call) {
        super.onCallAdded(telecomCall)
        Log.d(TAG, "onCallAdded: state=${telecomCall.state}")

        serviceScope.launch {
            val deviceId = deviceEngine.getLocalDeviceId()
            val details = telecomCall.details
            val phoneNumber = details.handle?.schemeSpecificPart ?: ""
            val displayName = details.callerDisplayName ?: ""

            when (telecomCall.state) {
                Call.STATE_RINGING -> {
                    // Incoming call from the network
                    val callId = CryptoUtils.generateId("call")
                    telecomCallToId[telecomCall.hashCode()] = callId
                    callEngine.onIncomingCall(
                        callId = callId,
                        phoneNumber = phoneNumber,
                        displayName = displayName.ifBlank { null },
                        deviceId = deviceId,
                        subscriptionId = 0
                    )
                }
                Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                    // Outbound call — try to match to an existing CallEngine record
                    // (created by makeCall() or API). If no match, create a new tracked record.
                    if (!telecomCallToId.containsKey(telecomCall.hashCode())) {
                        val existing = callEngine.findRecentOutboundCall(phoneNumber)
                        if (existing != null) {
                            Log.d(TAG, "Matched outbound call to existing record: ${existing.id}")
                            telecomCallToId[telecomCall.hashCode()] = existing.id
                        } else {
                            val callId = CryptoUtils.generateId("call")
                            telecomCallToId[telecomCall.hashCode()] = callId
                            Log.d(TAG, "No existing record found for $phoneNumber, created new callId=$callId")
                        }
                    }
                }
                else -> {
                    // Active, held, etc. handled by onCallStateChanged
                }
            }

            // Register state callback
            telecomCall.registerCallback(object : Call.Callback() {
                override fun onStateChanged(tc: Call, state: Int) {
                    handleStateChange(tc, state, deviceId)
                }

                override fun onDetailsChanged(tc: Call, details: Call.Details) {
                    // Can update display name, etc. if needed
                }
            })
        }
    }

    override fun onCallRemoved(telecomCall: Call) {
        super.onCallRemoved(telecomCall)
        val callId = telecomCallToId.remove(telecomCall.hashCode())
        if (callId != null) {
            serviceScope.launch {
                callEngine.hangupCall(callId)
            }
        }
    }

    private fun handleStateChange(telecomCall: Call, state: Int, deviceId: String) {
        val callId = telecomCallToId[telecomCall.hashCode()] ?: return
        serviceScope.launch {
            when (state) {
                Call.STATE_ACTIVE -> callEngine.answerCall(callId)
                Call.STATE_HOLDING -> callEngine.holdCall(callId)
                Call.STATE_DISCONNECTED -> callEngine.hangupCall(callId)
                Call.STATE_DISCONNECTING -> callEngine.hangupCall(callId)
                else -> { /* no action for other states */ }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
