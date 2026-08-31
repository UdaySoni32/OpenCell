package io.opencell.core.model

import kotlinx.serialization.Serializable

/**
 * Capability status for feature detection.
 * Never assume support - always verify against actual device capabilities.
 */
enum class CapabilityStatus {
    SUPPORTED,
    UNSUPPORTED,
    PERMISSION_REQUIRED,
    EXPERIMENTAL,
    DEVICE_DEPENDENT,
    OEM_DEPENDENT,
    CARRIER_DEPENDENT,
    UNKNOWN
}

/**
 * Normalized capability identifiers.
 */
object Capabilities {
    const val SMS_SEND = "sms.send"
    const val SMS_RECEIVE = "sms.receive"
    const val MMS_SEND = "mms.send"
    const val MMS_RECEIVE = "mms.receive"
    const val CALLS_OUTGOING = "calls.outgoing"
    const val CALLS_INCOMING = "calls.incoming"
    const val CALL_CONTROL = "calls.control"
    const val CALL_RECORDING = "calls.recording"
    const val AUDIO_RX = "calls.audio.rx"
    const val AUDIO_TX = "calls.audio.tx"
    const val AUDIO_FULL_DUPLEX = "calls.audio.full_duplex"
    const val USSD = "ussd"
    const val SIM_INFO = "sim.info"
    const val NETWORK_INFO = "network.info"
    const val SIGNAL_INFO = "signal.info"
    const val CONTACTS_READ = "contacts.read"
    const val CONTACTS_WRITE = "contacts.write"
}

@Serializable
data class Capability(
    val capability: String,
    val status: CapabilityStatus,
    val reason: String = "",
    val deviceModel: String = "",
    val androidVersion: String = ""
)

@Serializable
data class CapabilityReport(
    val deviceId: String,
    val timestamp: Long,
    val capabilities: List<Capability>
)
