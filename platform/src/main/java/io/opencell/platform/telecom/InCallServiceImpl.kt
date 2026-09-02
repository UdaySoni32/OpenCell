package io.opencell.platform.telecom

import android.database.Cursor
import android.net.Uri
import android.os.IBinder
import android.provider.ContactsContract
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
import kotlinx.coroutines.withContext

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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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

    private val callUiDelegate: CallUiDelegate by lazy {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            PlatformEntryPoint::class.java
        )
        entryPoint.callUiDelegate()
    }

    // Map from Telecom Call object hashCode → our internal call ID
    private val telecomCallToId = mutableMapOf<Int, String>()

    // Track whether we've launched the incoming call activity for each call
    private val launchedIncomingFor = mutableSetOf<Int>()

    override fun onBind(intent: android.content.Intent?): IBinder? = super.onBind(intent)

    override fun onCallAdded(telecomCall: Call) {
        super.onCallAdded(telecomCall)
        Log.d(TAG, "onCallAdded: state=${telecomCall.state}")

        serviceScope.launch {
            val deviceId = withContext(Dispatchers.IO) { deviceEngine.getLocalDeviceId() }
            val details = telecomCall.details
            val phoneNumber = details.handle?.schemeSpecificPart ?: ""
            val carrierDisplayName = details.callerDisplayName ?: ""

            // Resolve contact name from the phone's contacts provider
            val contactName = withContext(Dispatchers.IO) {
                lookupContactName(phoneNumber)
            }
            val displayName = contactName ?: carrierDisplayName.ifBlank { null }
            Log.d(TAG, "Resolved caller: name=$displayName, number=$phoneNumber")

            when (telecomCall.state) {
                Call.STATE_RINGING -> {
                    // Incoming call from the network
                    val callId = CryptoUtils.generateId("call")
                    telecomCallToId[telecomCall.hashCode()] = callId
                    withContext(Dispatchers.IO) {
                        callEngine.onIncomingCall(
                            callId = callId,
                            phoneNumber = phoneNumber,
                            displayName = displayName,
                            deviceId = deviceId,
                            subscriptionId = 0
                        )
                    }
                    // Launch our custom incoming call UI
                    withContext(Dispatchers.Main) {
                        if (!launchedIncomingFor.contains(telecomCall.hashCode())) {
                            launchedIncomingFor.add(telecomCall.hashCode())
                            callUiDelegate.showIncomingCall(
                                applicationContext,
                                callId,
                                phoneNumber,
                                displayName
                            )
                        }
                    }
                }
                Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                    // Outbound call — try to match to an existing CallEngine record
                    if (!telecomCallToId.containsKey(telecomCall.hashCode())) {
                        val existing = withContext(Dispatchers.IO) {
                            callEngine.findRecentOutboundCall(phoneNumber)
                        }
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

            // Register state callback — must be on main/looper thread
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
        val details = telecomCall.details
        val phoneNumber = details.handle?.schemeSpecificPart ?: ""
        val carrierDisplayName = details.callerDisplayName ?: ""

        serviceScope.launch {
            when (state) {
                Call.STATE_ACTIVE -> {
                    callEngine.answerCall(callId)
                    // Launch active call UI if we didn't come from our IncomingCallActivity
                    // (e.g. user answered from system notification)
                    withContext(Dispatchers.Main) {
                        val contactName = withContext(Dispatchers.IO) {
                            lookupContactName(phoneNumber)
                        }
                        val name = contactName ?: carrierDisplayName.ifBlank { null }
                        callUiDelegate.showActiveCall(
                            applicationContext,
                            callId,
                            phoneNumber,
                            name
                        )
                    }
                }
                Call.STATE_HOLDING -> callEngine.holdCall(callId)
                Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                    callEngine.hangupCall(callId)
                    // Clear tracking state
                    launchedIncomingFor.remove(telecomCall.hashCode())
                    telecomCallToId.remove(telecomCall.hashCode())
                }
                else -> { /* no action for other states */ }
            }
        }
    }

    /**
     * Look up a contact name from the phone's contacts provider.
     */
    private fun lookupContactName(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            if (cursor != null && cursor.moveToFirst()) {
                cursor.getString(0)
            } else null
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
