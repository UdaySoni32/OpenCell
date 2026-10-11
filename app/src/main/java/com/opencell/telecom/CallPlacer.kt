package com.opencell.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log

/** Places SIM calls through Telecom so our InCallService handles them. */
class CallPlacer(private val context: Context) {

    /** Returns false if the number is blank or CALL_PHONE is missing. */
    fun placeCall(number: String): Boolean {
        val cleaned = number.trim()
        if (cleaned.isEmpty()) return false
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return false
        val telecom = context.getSystemService(TelecomManager::class.java)
        return runCatching { telecom.placeCall(Uri.fromParts("tel", cleaned, null), Bundle()) }
            .onFailure { Log.e("CallPlacer", "placeCall failed", it) }
            .isSuccess
    }
}
