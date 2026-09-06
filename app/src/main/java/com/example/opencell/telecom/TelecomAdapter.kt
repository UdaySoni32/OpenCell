package com.example.opencell.telecom

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

open class TelecomAdapter(private val context: Context) {

    private val telecomManager: TelecomManager? by lazy {
        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    }

    private val telephonyManager: TelephonyManager? by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }

    private val audioManager: AudioManager? by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    open fun isTelephonyCapable(): Boolean {
        val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val phoneType = telephonyManager?.phoneType ?: TelephonyManager.PHONE_TYPE_NONE
        return hasFeature && phoneType != TelephonyManager.PHONE_TYPE_NONE
    }

    open fun hasCallPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    open fun placeCall(phoneNumber: String): Boolean {
        if (!hasCallPermission() || !isTelephonyCapable()) {
            return false
        }
        val manager = telecomManager ?: return false
        val uri = Uri.fromParts("tel", phoneNumber, null)
        val extras = Bundle()
        return try {
            manager.placeCall(uri, extras)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    open fun answerCall(call: Call?): Boolean {
        if (call == null) return false
        return try {
            call.answer(0)
            true
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    open fun rejectCall(call: Call?): Boolean {
        if (call == null) return false
        return try {
            if (call.state == Call.STATE_RINGING) {
                call.reject(false, null)
            } else {
                call.disconnect()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    open fun hangupCall(call: Call?): Boolean {
        if (call == null) return false
        return try {
            call.disconnect()
            true
        } catch (_: Exception) {
            false
        }
    }

    open fun setMute(muted: Boolean, inCallService: InCallService?): Boolean {
        return try {
            if (inCallService != null) {
                inCallService.setMuted(muted)
                true
            } else {
                audioManager?.isMicrophoneMute = muted
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    open fun setSpeaker(speakerOn: Boolean, inCallService: InCallService?): Boolean {
        return try {
            if (inCallService != null) {
                val route = if (speakerOn) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
                inCallService.setAudioRoute(route)
                true
            } else {
                audioManager?.isSpeakerphoneOn = speakerOn
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    open fun setHold(call: Call?, hold: Boolean): Boolean {
        if (call == null) return false
        return try {
            if (hold) {
                call.hold()
            } else {
                call.unhold()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    open fun playDtmfTone(digit: Char, call: Call?): Boolean {
        if (call == null) return false
        return try {
            call.playDtmfTone(digit)
            true
        } catch (_: Exception) {
            false
        }
    }

    open fun stopDtmfTone(call: Call?): Boolean {
        if (call == null) return false
        return try {
            call.stopDtmfTone()
            true
        } catch (_: Exception) {
            false
        }
    }
}
