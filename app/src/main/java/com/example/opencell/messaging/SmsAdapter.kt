package com.example.opencell.messaging

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

open class SmsAdapter(private val context: Context) {

    private val telephonyManager: TelephonyManager? by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }

    private val smsManager: SmsManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    open fun isSmsCapable(): Boolean {
        val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        @Suppress("DEPRECATION")
        val capable = telephonyManager?.isSmsCapable ?: hasFeature
        return hasFeature && capable
    }

    open fun hasSmsPermissions(): Boolean {
        val sendGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val receiveGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        return sendGranted && receiveGranted
    }

    open fun sendSms(
        destination: String,
        text: String,
        sentIntent: PendingIntent?,
        deliveryIntent: PendingIntent?
    ): Boolean {
        if (!hasSmsPermissions() || !isSmsCapable()) {
            return false
        }
        val manager = smsManager ?: return false

        return try {
            val parts = manager.divideMessage(text)
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent?>()
                val deliveryIntents = ArrayList<PendingIntent?>()
                for (i in parts.indices) {
                    sentIntents.add(if (i == 0) sentIntent else null)
                    deliveryIntents.add(if (i == 0) deliveryIntent else null)
                }
                manager.sendMultipartTextMessage(
                    destination,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            } else {
                manager.sendTextMessage(
                    destination,
                    null,
                    text,
                    sentIntent,
                    deliveryIntent
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
