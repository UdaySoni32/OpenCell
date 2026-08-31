package io.opencell.platform.capabilities

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.opencell.core.model.*
import io.opencell.platform.devices.DeviceEngine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CapabilityEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceEngine: DeviceEngine
) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

    suspend fun getAllCapabilities(): List<Capability> {
        val device = deviceEngine.getOrCreateLocalDevice()
        val hasSim = deviceEngine.getSimInfo().isNotEmpty()
        val isDefaultDialer = telecomManager?.defaultDialerPackage == context.packageName
        val isDefaultSms = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

        return listOf(
            checkSmsSend(hasSim),
            checkSmsReceive(hasSim, isDefaultSms),
            checkMmsSend(hasSim),
            checkMmsReceive(hasSim, isDefaultSms),
            checkCallsOutgoing(hasSim),
            checkCallsIncoming(hasSim, isDefaultDialer),
            checkCallControl(),
            checkCallRecording(),
            checkAudioRx(),
            checkAudioTx(),
            checkAudioFullDuplex(),
            checkUssd(),
            checkSimInfo(),
            checkNetworkInfo(),
            checkSignalInfo(),
            checkContactsRead(),
            checkContactsWrite()
        ).map { it.copy(deviceModel = "${device.manufacturer} ${device.model}", androidVersion = device.androidVersion) }
    }

    private fun checkSmsSend(hasSim: Boolean): Capability {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        return when {
            !hasSim -> Capability(Capabilities.SMS_SEND, CapabilityStatus.CARRIER_DEPENDENT, "No SIM detected")
            !hasPermission -> Capability(Capabilities.SMS_SEND, CapabilityStatus.PERMISSION_REQUIRED, "SEND_SMS permission required")
            else -> Capability(Capabilities.SMS_SEND, CapabilityStatus.SUPPORTED)
        }
    }

    private fun checkSmsReceive(hasSim: Boolean, isDefaultSms: Boolean): Capability {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        return when {
            !hasSim -> Capability(Capabilities.SMS_RECEIVE, CapabilityStatus.CARRIER_DEPENDENT, "No SIM detected")
            !hasPermission -> Capability(Capabilities.SMS_RECEIVE, CapabilityStatus.PERMISSION_REQUIRED, "RECEIVE_SMS permission required")
            !isDefaultSms -> Capability(Capabilities.SMS_RECEIVE, CapabilityStatus.PERMISSION_REQUIRED, "Must be default SMS app to receive messages")
            else -> Capability(Capabilities.SMS_RECEIVE, CapabilityStatus.SUPPORTED)
        }
    }

    private fun checkMmsSend(hasSim: Boolean): Capability {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        return when {
            !hasSim -> Capability(Capabilities.MMS_SEND, CapabilityStatus.CARRIER_DEPENDENT, "No SIM detected")
            !hasPermission -> Capability(Capabilities.MMS_SEND, CapabilityStatus.PERMISSION_REQUIRED, "SEND_SMS permission required")
            else -> Capability(Capabilities.MMS_SEND, CapabilityStatus.EXPERIMENTAL, "MMS sending varies by carrier and device")
        }
    }

    private fun checkMmsReceive(hasSim: Boolean, isDefaultSms: Boolean): Capability {
        return when {
            !hasSim -> Capability(Capabilities.MMS_RECEIVE, CapabilityStatus.CARRIER_DEPENDENT, "No SIM detected")
            !isDefaultSms -> Capability(Capabilities.MMS_RECEIVE, CapabilityStatus.PERMISSION_REQUIRED, "Must be default SMS app")
            else -> Capability(Capabilities.MMS_RECEIVE, CapabilityStatus.EXPERIMENTAL, "MMS receive varies by carrier")
        }
    }

    private fun checkCallsOutgoing(hasSim: Boolean): Capability {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        return when {
            !hasSim -> Capability(Capabilities.CALLS_OUTGOING, CapabilityStatus.CARRIER_DEPENDENT, "No SIM detected")
            !hasPermission -> Capability(Capabilities.CALLS_OUTGOING, CapabilityStatus.PERMISSION_REQUIRED, "CALL_PHONE permission required")
            else -> Capability(Capabilities.CALLS_OUTGOING, CapabilityStatus.SUPPORTED)
        }
    }

    private fun checkCallsIncoming(hasSim: Boolean, isDefaultDialer: Boolean): Capability {
        return when {
            !hasSim -> Capability(Capabilities.CALLS_INCOMING, CapabilityStatus.CARRIER_DEPENDENT, "No SIM detected")
            !isDefaultDialer -> Capability(Capabilities.CALLS_INCOMING, CapabilityStatus.PERMISSION_REQUIRED, "Must be default dialer to handle incoming calls")
            else -> Capability(Capabilities.CALLS_INCOMING, CapabilityStatus.SUPPORTED)
        }
    }

    private fun checkCallControl() = Capability(Capabilities.CALL_CONTROL, CapabilityStatus.SUPPORTED, "Basic call control via Telecom framework")

    private fun checkCallRecording(): Capability {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Capability(Capabilities.CALL_RECORDING, CapabilityStatus.DEVICE_DEPENDENT, "Varies by device and OEM")
        } else {
            Capability(Capabilities.CALL_RECORDING, CapabilityStatus.UNSUPPORTED, "Not available on Android < 10")
        }
    }

    private fun checkAudioRx() = Capability(Capabilities.AUDIO_RX, CapabilityStatus.EXPERIMENTAL, "Raw carrier-call PCM audio access not guaranteed")
    private fun checkAudioTx() = Capability(Capabilities.AUDIO_TX, CapabilityStatus.EXPERIMENTAL, "Raw carrier-call PCM audio injection not guaranteed")
    private fun checkAudioFullDuplex() = Capability(Capabilities.AUDIO_FULL_DUPLEX, CapabilityStatus.EXPERIMENTAL, "Long-term goal")

    private fun checkUssd(): Capability {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        return if (hasPermission) Capability(Capabilities.USSD, CapabilityStatus.EXPERIMENTAL, "Varies by carrier")
        else Capability(Capabilities.USSD, CapabilityStatus.PERMISSION_REQUIRED, "READ_PHONE_STATE permission required")
    }

    private fun checkSimInfo(): Capability {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        return if (hasPermission) Capability(Capabilities.SIM_INFO, CapabilityStatus.SUPPORTED)
        else Capability(Capabilities.SIM_INFO, CapabilityStatus.PERMISSION_REQUIRED, "READ_PHONE_STATE permission required")
    }

    private fun checkNetworkInfo(): Capability {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED
        return if (hasPermission) Capability(Capabilities.NETWORK_INFO, CapabilityStatus.SUPPORTED)
        else Capability(Capabilities.NETWORK_INFO, CapabilityStatus.PERMISSION_REQUIRED, "ACCESS_NETWORK_STATE permission required")
    }

    private fun checkSignalInfo(): Capability {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        return if (hasPermission) Capability(Capabilities.SIGNAL_INFO, CapabilityStatus.SUPPORTED)
        else Capability(Capabilities.SIGNAL_INFO, CapabilityStatus.PERMISSION_REQUIRED, "READ_PHONE_STATE permission required")
    }

    private fun checkContactsRead(): Capability {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        return if (hasPermission) Capability(Capabilities.CONTACTS_READ, CapabilityStatus.SUPPORTED)
        else Capability(Capabilities.CONTACTS_READ, CapabilityStatus.PERMISSION_REQUIRED, "READ_CONTACTS permission required")
    }

    private fun checkContactsWrite(): Capability {
        val hasPermission = context.checkSelfPermission(android.Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
        return if (hasPermission) Capability(Capabilities.CONTACTS_WRITE, CapabilityStatus.SUPPORTED)
        else Capability(Capabilities.CONTACTS_WRITE, CapabilityStatus.PERMISSION_REQUIRED, "WRITE_CONTACTS permission required")
    }
}
