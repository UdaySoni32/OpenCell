package com.opencell.voice

/**
 * How the AI hears a SIM call without root (see design spec). Best first.
 * Root users get [SHIZUKU] through Sui, which speaks the Shizuku API.
 */
enum class CallAudioMode(val label: String, val canSpeak: Boolean, val canListen: Boolean) {
    SHIZUKU("Shizuku: hears the caller directly, speaks via speakerphone", canSpeak = true, canListen = true),
    SPEAKER("Speaker: speakerphone + microphone", canSpeak = true, canListen = true),
    TEXT("Text only: decline and continue by SMS", canSpeak = false, canListen = false),
}

data class AudioCapabilities(val shizukuReady: Boolean, val hasMicPermission: Boolean)

object CallAudioModes {
    fun available(caps: AudioCapabilities): List<CallAudioMode> = buildList {
        if (caps.shizukuReady) add(CallAudioMode.SHIZUKU)
        if (caps.hasMicPermission) add(CallAudioMode.SPEAKER)
        add(CallAudioMode.TEXT)
    }

    /** The mode a voice session uses, or null if only text is possible. */
    fun forVoice(caps: AudioCapabilities): CallAudioMode? = available(caps).firstOrNull { it.canSpeak }

    /** Silent transcription of calls the user takes needs the clean Shizuku feed; speaker mode would hear only you. */
    fun forListening(caps: AudioCapabilities): CallAudioMode? = CallAudioMode.SHIZUKU.takeIf { caps.shizukuReady }
}
