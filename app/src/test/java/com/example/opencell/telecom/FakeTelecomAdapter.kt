package com.example.opencell.telecom

import android.content.Context
import android.content.ContextWrapper
import android.telecom.Call
import android.telecom.InCallService

class TestContext : ContextWrapper(null)

class FakeTelecomAdapter : TelecomAdapter(TestContext()) {
    var isCapable = true
    var hasPermission = true
    var placeCallResult = true

    override fun isTelephonyCapable(): Boolean = isCapable
    override fun hasCallPermission(): Boolean = hasPermission
    override fun placeCall(phoneNumber: String): Boolean = placeCallResult
    override fun answerCall(call: Call?): Boolean = true
    override fun rejectCall(call: Call?): Boolean = true
    override fun hangupCall(call: Call?): Boolean = true
    override fun setMute(muted: Boolean, inCallService: InCallService?): Boolean = true
    override fun setSpeaker(speakerOn: Boolean, inCallService: InCallService?): Boolean = true
    override fun playDtmfTone(digit: Char, call: Call?): Boolean = true
    override fun stopDtmfTone(call: Call?): Boolean = true
}
